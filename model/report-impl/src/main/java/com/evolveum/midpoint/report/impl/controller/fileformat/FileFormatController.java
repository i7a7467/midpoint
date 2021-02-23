/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.report.impl.controller.fileformat;

import java.io.IOException;
import java.util.*;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.model.api.util.DashboardUtils;
import com.evolveum.midpoint.model.common.util.DefaultColumnUtils;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.NameItemPathSegment;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.report.impl.ReportServiceImpl;
import com.evolveum.midpoint.report.impl.ReportUtils;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author skublik
 */

public abstract class FileFormatController {

    private static final Trace LOGGER = TraceManager.getTrace(FileFormatController.class);

    protected static final String LABEL_COLUMN = "label";
    protected static final String NUMBER_COLUMN = "number";
    protected static final String STATUS_COLUMN = "status";

    private static final Set<String> HEADS_OF_WIDGET =
            ImmutableSet.of(LABEL_COLUMN, NUMBER_COLUMN, STATUS_COLUMN);

    private final ReportServiceImpl reportService;
    private final FileFormatConfigurationType fileFormatConfiguration;
    private final ReportType report;

    public FileFormatController(FileFormatConfigurationType fileFormatConfiguration, ReportType report, ReportServiceImpl reportService) {
        this.fileFormatConfiguration = fileFormatConfiguration;
        this.reportService = reportService;
        this.report = report;
    }

    protected ReportServiceImpl getReportService() {
        return reportService;
    }

    public FileFormatConfigurationType getFileFormatConfiguration() {
        return fileFormatConfiguration;
    }

    protected static Set<String> getHeadsOfWidget() {
        return HEADS_OF_WIDGET;
    }

    public abstract byte[] processDashboard(DashboardReportEngineConfigurationType dashboardConfig, Task task, OperationResult result) throws Exception;

    public abstract byte[] processCollection(String nameOfReport, ObjectCollectionReportEngineConfigurationType collectionConfig, Task task, OperationResult result) throws Exception;

    protected void recordProgress(Task task, long progress, OperationResult opResult, Trace logger) {
        try {
            task.setProgressImmediate(progress, opResult);
        } catch (ObjectNotFoundException e) {             // these exceptions are of so little probability and harmless, so we just log them and do not report higher
            LoggingUtils.logException(logger, "Couldn't record progress to task {}, probably because the task does not exist anymore", e, task);
        } catch (SchemaException e) {
            LoggingUtils.logException(logger, "Couldn't record progress to task {}, due to unexpected schema exception", e, task);
        }
    }

    public abstract String getTypeSuffix();

    public abstract String getType();

//    protected String getMessage(Enum e) {
//        return getMessage(e.getDeclaringClass().getSimpleName() + '.' + e.name());
//    }

    protected String getMessage(String key) {
        return getMessage(key, null);
    }

    protected String getMessage(String key, Object... params) {
        return getReportService().getLocalizationService().translate(key, params, Locale.getDefault(), key);
    }

    protected String getRealValueAsString(GuiObjectColumnType column, PrismContainer<? extends Containerable> object, ItemPath itemPath,
            ExpressionType expression, Task task, OperationResult result) {
        Item valueObject = object;

        if (itemPath != null && !DefaultColumnUtils.isSpecialColumn(itemPath, object)) {
            Iterator<?> iterator = itemPath.getSegments().iterator();
            while (iterator.hasNext()) {
                Object segment = iterator.next();
                QName name;
                if (segment instanceof QName) {
                    name = (QName) segment;
                } else if (segment instanceof NameItemPathSegment) {
                    name = ((NameItemPathSegment) segment).getName();
                } else {
                    continue;
                }
                if (valueObject == null) {
                    break;
                }
                valueObject = (Item) valueObject.find(ItemPath.create(name));
                if (valueObject instanceof PrismProperty && iterator.hasNext()) {
                    throw new IllegalArgumentException("Found object is PrismProperty, but ItemPath isn't empty");
                }
                if (valueObject instanceof PrismReference) {
                    if (valueObject.isSingleValue()) {
                        Referencable ref = ((PrismReference) valueObject).getRealValue();
                        if (iterator.hasNext()) {
                            valueObject = getObjectFromReference(ref);
                        }
                    } else {
                        if (iterator.hasNext()) {
                            throw new IllegalArgumentException("Found reference object is multivalue, but ItemPath isn't empty");
                        }
                    }

                }
            }
        }
        if (expression != null) {
            Object value = evaluateExportExpression(expression, valueObject, task, result);
            if (value instanceof List) {
                return processListOfRealValues((List) value);
            }
            return processListOfRealValues(Collections.singletonList(value));
        }
        if (DisplayValueType.NUMBER.equals(column.getDisplayValue())) {
            if (valueObject == null) {
                return "0";
            }
            return String.valueOf(valueObject.getValues().size());
        }
        if (itemPath == null) {
            throw new IllegalArgumentException("Path and expression for column " + column.getName() + " is null");
        }
        if (DefaultColumnUtils.isSpecialColumn(itemPath, object)) {
            return DefaultColumnUtils.processSpecialColumn(itemPath, object, getReportService().getLocalizationService());
        }
        if (valueObject instanceof PrismContainer) {
            throw new IllegalArgumentException("Found object is PrismContainer, but expression is null and should be display real value");
        }
        if (valueObject == null) {
            return "";
        }
        @NotNull List<PrismValue> values = valueObject.getValues();
        return processListOfRealValues(values);
    }

    private <O extends Object> String processListOfRealValues(Collection<?> values) {
        StringBuilder sb = new StringBuilder();
        values.forEach(value -> {
            if (!sb.toString().isEmpty()) {
                appendMultivalueDelimiter(sb);
            }
            if (value instanceof PrismPropertyValue) {
                String stringValue;
                O realObject = ((PrismPropertyValue<O>) value).getRealValue();
                if (realObject == null) {
                    stringValue = "";
                } else if (realObject instanceof Collection) {
                    stringValue = processListOfRealValues((Collection) realObject);
                } else if (realObject instanceof Enum) {
                    stringValue = ReportUtils.prettyPrintForReport((Enum) realObject);
                } else if (realObject instanceof XMLGregorianCalendar) {
                    stringValue = ReportUtils.prettyPrintForReport((XMLGregorianCalendar) realObject);
                } else if (realObject instanceof ObjectDeltaOperationType) {
                    try {
                        ObjectDeltaOperation convertedDelta = DeltaConvertor.createObjectDeltaOperation((ObjectDeltaOperationType) realObject, getReportService().getPrismContext());
                        stringValue = ReportUtils.printDelta(convertedDelta);
                    } catch (SchemaException e) {
                        LOGGER.error("Couldn't convert delta from ObjectDeltaOperationType to ObjectDeltaOperation " + realObject.toString());
                        stringValue = "";
                    }
                } else {
                    stringValue = ReportUtils.prettyPrintForReport(realObject);
                }
                sb.append(stringValue);
            } else if (value instanceof PrismReferenceValue) {
                sb.append(getObjectNameFromRef(((PrismReferenceValue) value).getRealValue()));
            } else {
                sb.append(ReportUtils.prettyPrintForReport(value));
            }
        });
        return sb.toString();
    }

    private String getObjectNameFromRef(Referencable ref) {
        if (ref == null) {
            return "";
        }
        if (ref.getTargetName() != null && ref.getTargetName().getOrig() != null) {
            return ref.getTargetName().getOrig();
        }
        PrismObject object = getObjectFromReference(ref);

        if (object == null) {
            return ref.getOid();
        }

        if (object.getName() == null || object.getName().getOrig() == null) {
            return "";
        }
        return object.getName().getOrig();
    }

    protected abstract void appendMultivalueDelimiter(StringBuilder sb);

    private Object evaluateExportExpression(ExpressionType expression, Item valueObject, Task task, OperationResult result) {
        Object object;
        if (valueObject == null) {
            object = null;
        } else {
            object = valueObject.getRealValue();
        }
        return evaluateExportExpression(expression, object, task, result);
    }

    private Object evaluateExportExpression(ExpressionType expression, Object valueObject, Task task, OperationResult result) {

        VariablesMap variables = new VariablesMap();
        if (valueObject == null) {
            variables.put(ExpressionConstants.VAR_OBJECT, null, Object.class);
        } else {
            variables.put(ExpressionConstants.VAR_OBJECT, valueObject, valueObject.getClass());
        }
        Object values = null;
        try {
            values = ExpressionUtil.evaluateExpression(null, variables, null, expression,
                    determineExpressionProfile(result), getReportService().getExpressionFactory(), "value for column", task, result);
        } catch (SchemaException | ExpressionEvaluationException | ObjectNotFoundException | CommunicationException
                | ConfigurationException | SecurityViolationException e) {
            LOGGER.error("Couldn't execute expression " + expression, e);
        }
        if (values == null || (values instanceof Collection && ((Collection) values).isEmpty())) {
            return "";
        }
        return values;
    }

    protected Object evaluateImportExpression(ExpressionType expression, String input, Task task, OperationResult result) {
        VariablesMap variables = new VariablesMap();
        variables.put(ExpressionConstants.VAR_INPUT, input, String.class);
        return evaluateImportExpression(expression, variables, task, result);
    }

    protected Object evaluateImportExpression(ExpressionType expression, List<String> input, Task task, OperationResult result) {
        VariablesMap variables = new VariablesMap();
        variables.put(ExpressionConstants.VAR_INPUT, input, List.class);
        return evaluateImportExpression(expression, variables, task, result);
    }

    private Object evaluateImportExpression(ExpressionType expression, VariablesMap variables, Task task, OperationResult result) {
        Object value = null;
        try {
            value = ExpressionUtil.evaluateExpression(null, variables, null, expression,
                    determineExpressionProfile(result), getReportService().getExpressionFactory(), "value for column", task, result);
        } catch (SchemaException | ExpressionEvaluationException | ObjectNotFoundException | CommunicationException
                | ConfigurationException | SecurityViolationException e) {
            LOGGER.error("Couldn't execute expression " + expression, e);
        }
        if (value instanceof PrismPropertyValue) {
            return ((PrismPropertyValue) value).getRealValue();
        }
        return value;
    }

    private ExpressionProfile determineExpressionProfile(OperationResult result) throws SchemaException, ConfigurationException {
        return getReportService().determineExpressionProfile(report.asPrismContainer(), result);
    }

    protected String getColumnLabel(GuiObjectColumnType column, PrismContainerDefinition objectDefinition) {
        ItemPath path = column.getPath() == null ? null : column.getPath().getItemPath();

        DisplayType columnDisplay = column.getDisplay();
        String label;
        if (columnDisplay != null && columnDisplay.getLabel() != null) {
            label = getMessage(columnDisplay.getLabel().getOrig());
        } else {

            String name = column.getName();
            if (path != null) {
                ItemDefinition def = objectDefinition.findItemDefinition(path);
                if (def == null) {
                    throw new IllegalArgumentException("Could'n find item for path " + path);
                }
                String displayName = def.getDisplayName();
                label = getMessage(displayName);
            } else {
                label = name;
            }
        }
        return label;
    }

    protected PrismObject<ObjectType> getObjectFromReference(Referencable ref) {
        Task task = getReportService().getTaskManager().createTaskInstance("Get object");
        Class<ObjectType> type = getReportService().getPrismContext().getSchemaRegistry().determineClassForType(ref.getType());

        if (ref.asReferenceValue().getObject() != null) {
            return ref.asReferenceValue().getObject();
        }

        PrismObject<ObjectType> object = null;
        try {
            object = getReportService().getModelService().getObject(type, ref.getOid(), null, task, task.getResult());
        } catch (Exception e) {
            LOGGER.error("Couldn't get object from objectRef " + ref, e);
        }
        return object;
    }

    protected QName resolveTypeQname(CollectionRefSpecificationType collectionRef, CompiledObjectCollectionView compiledCollection) {
        QName type;
        if (collectionRef.getCollectionRef() != null) {
            ObjectCollectionType collection = (ObjectCollectionType) getObjectFromReference(collectionRef.getCollectionRef()).asObjectable();
            if (collection.getAuditSearch() != null) {
                type = AuditEventRecordType.COMPLEX_TYPE;
            } else {
                type = collection.getType();
            }
        } else if (collectionRef.getBaseCollectionRef() != null && collectionRef.getBaseCollectionRef().getCollectionRef() != null) {
            ObjectCollectionType collection = (ObjectCollectionType) getObjectFromReference(collectionRef.getBaseCollectionRef().getCollectionRef()).asObjectable();
            type = collection.getType();
        } else {
            type = compiledCollection.getContainerType();
        }
        if (type == null) {
            LOGGER.error("Couldn't define type for objects");
            throw new IllegalArgumentException("Couldn't define type for objects");
        }
        return type;
    }

    protected Class<ObjectType> resolveType(CollectionRefSpecificationType collectionRef, CompiledObjectCollectionView compiledCollection) {
        QName type = resolveTypeQname(collectionRef, compiledCollection);
        return (Class<ObjectType>) getReportService().getPrismContext().getSchemaRegistry()
                .getCompileTimeClassForObjectType(type);
    }

    protected boolean isAuditCollection(CollectionRefSpecificationType collection, Task task, OperationResult result) {
        return DashboardUtils.isAuditCollection(collection, getReportService().getModelService(), task, result);
    }

    protected PrismContainer<? extends Containerable> getAuditRecordAsContainer(AuditEventRecordType record) throws SchemaException {
        PrismContainerValue prismValue = record.asPrismContainerValue();
        prismValue.setPrismContext(getReportService().getPrismContext());
        return prismValue.asSingleValuedContainer(AuditEventRecordType.COMPLEX_TYPE);
    }

    public abstract void importCollectionReport(ReportType report, VariablesMap listOfVariables, RunningTask task, OperationResult result);

    public abstract List<VariablesMap> createVariablesFromFile(ReportType report, ReportDataType reportData, boolean useImportScript, Task task, OperationResult result) throws IOException;

    protected <T extends Object> boolean evaluateCondition(ExpressionType condition, T value, Task task, OperationResult result)
            throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
        VariablesMap variables = new VariablesMap();
        variables.put(ExpressionConstants.VAR_OBJECT, value, value.getClass());
        PrismPropertyValue<Boolean> conditionValue = ExpressionUtil.evaluateCondition(variables, condition, null, getReportService().getExpressionFactory(),
                "Evaluate condition", task, result);
        if (conditionValue == null || Boolean.FALSE.equals(conditionValue.getRealValue())) {
            return false;
        }
        return true;
    }
}
