/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.common.mapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.model.common.ModelCommonBeans;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.util.ObjectDeltaObject;
import com.evolveum.midpoint.repo.common.expression.ConfigurableValuePolicySupplier;
import com.evolveum.midpoint.repo.common.expression.Source;
import com.evolveum.midpoint.repo.common.expression.VariableProducer;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Builder is used to construct a configuration of Mapping object, which - after building - becomes
 * immutable.
 * <p>
 * In order to provide backward-compatibility with existing use of Mapping object, the builder has
 * also traditional setter methods. Both setters and "builder-style" methods MODIFY existing Builder
 * object (i.e. they do not create a new one).
 * <p>
 * TODO decide on which style of setters to keep (setters vs builder-style).
 */
@SuppressWarnings({ "unused", "UnusedReturnValue" })
public abstract class AbstractMappingBuilder<V extends PrismValue, D extends ItemDefinition, MBT extends AbstractMappingType, RT extends AbstractMappingBuilder<V, D, MBT, RT>> {

    private static final Trace LOGGER = TraceManager.getTrace(MappingImpl.class);

    private final VariablesMap variables = new VariablesMap();
    private MBT mappingBean;
    private MappingKindType mappingKind;
    private ItemPath implicitSourcePath; // for tracing purposes
    private ItemPath implicitTargetPath; // for tracing purposes
    private Source<?, ?> defaultSource;
    private final List<Source<?, ?>> additionalSources = new ArrayList<>();
    private D defaultTargetDefinition;
    private ExpressionProfile expressionProfile;
    private ItemPath defaultTargetPath;
    private Collection<V> originalTargetValues;
    private ObjectDeltaObject<?> sourceContext;
    private PrismContainerDefinition<?> targetContext;
    private OriginType originType;
    private ObjectType originObject;
    private ConfigurableValuePolicySupplier valuePolicySupplier;
    private VariableProducer variableProducer;
    private MappingPreExpression mappingPreExpression;
    private boolean conditionMaskOld = true;
    private boolean conditionMaskNew = true;
    private MappingSpecificationType mappingSpecification;
    private XMLGregorianCalendar now;
    private XMLGregorianCalendar defaultReferenceTime;
    private boolean profiling;
    private String contextDescription;
    private QName mappingQName;
    private RefinedObjectClassDefinition refinedObjectClassDefinition;
    private ModelCommonBeans beans;

    public abstract AbstractMappingImpl<V, D, MBT> build();

    //region Plain setters
    public RT variablesFrom(VariablesMap val) {
        variables.addVariableDefinitions(val);
        return typedThis();
    }

    public RT mappingBean(MBT val) {
        mappingBean = val;
        return typedThis();
    }

    public RT mappingKind(MappingKindType val) {
        mappingKind = val;
        return typedThis();
    }

    public RT implicitSourcePath(ItemPath val) {
        implicitSourcePath = val;
        return typedThis();
    }

    public RT implicitTargetPath(ItemPath val) {
        implicitTargetPath = val;
        return typedThis();
    }

    public RT defaultSource(Source<?, ?> val) {
        defaultSource = val;
        return typedThis();
    }

    public RT defaultTargetDefinition(D val) {
        defaultTargetDefinition = val;
        return typedThis();
    }

    public RT expressionProfile(ExpressionProfile val) {
        expressionProfile = val;
        return typedThis();
    }

    public RT defaultTargetPath(ItemPath val) {
        defaultTargetPath = val;
        return typedThis();
    }

    public RT originalTargetValues(Collection<V> values) {
        originalTargetValues = values;
        return typedThis();
    }

    public RT sourceContext(ObjectDeltaObject<?> val) {
        if (val.getDefinition() == null) {
            throw new IllegalArgumentException("Attempt to set mapping source context without a definition");
        }
        sourceContext = val;
        return typedThis();
    }

    public RT targetContext(PrismContainerDefinition<?> val) {
        targetContext = val;
        return typedThis();
    }

    public RT originType(OriginType val) {
        originType = val;
        return typedThis();
    }

    public RT originObject(ObjectType val) {
        originObject = val;
        return typedThis();
    }

    public RT valuePolicySupplier(ConfigurableValuePolicySupplier val) {
        valuePolicySupplier = val;
        return typedThis();
    }

    public RT variableResolver(VariableProducer<V> variableProducer) {
        this.variableProducer = variableProducer;
        return typedThis();
    }

    public RT mappingPreExpression(MappingPreExpression mappingPreExpression) {
        this.mappingPreExpression = mappingPreExpression;
        return typedThis();
    }

    public RT conditionMaskOld(boolean val) {
        conditionMaskOld = val;
        return typedThis();
    }

    public RT conditionMaskNew(boolean val) {
        conditionMaskNew = val;
        return typedThis();
    }

    public RT mappingSpecification(MappingSpecificationType val) {
        mappingSpecification = val;
        return typedThis();
    }

    public RT now(XMLGregorianCalendar val) {
        now = val;
        return typedThis();
    }

    public RT defaultReferenceTime(XMLGregorianCalendar val) {
        defaultReferenceTime = val;
        return typedThis();
    }

    public RT profiling(boolean val) {
        profiling = val;
        return typedThis();
    }

    public RT contextDescription(String val) {
        contextDescription = val;
        return typedThis();
    }

    public RT mappingQName(QName val) {
        mappingQName = val;
        return typedThis();
    }

    public RT refinedObjectClassDefinition(RefinedObjectClassDefinition val) {
        refinedObjectClassDefinition = val;
        return typedThis();
    }

    public RT beans(ModelCommonBeans val) {
        beans = val;
        return typedThis();
    }
    //endregion

    public RT rootNode(ObjectReferenceType objectRef) {
        return addVariableDefinition(null, objectRef);
    }

    public RT rootNode(ObjectDeltaObject<?> odo) {
        return addVariableDefinition(null, odo);
    }

    public <O extends ObjectType> RT rootNode(O objectType, PrismObjectDefinition<O> definition) {
        variables.put(null, objectType, definition);
        return typedThis();
    }

    public <O extends ObjectType> RT rootNode(PrismObject<? extends ObjectType> mpObject, PrismObjectDefinition<O> definition) {
        variables.put(null, mpObject, definition);
        return typedThis();
    }

    public RT addVariableDefinition(ExpressionVariableDefinitionType varDef) throws SchemaException {
        if (varDef.getObjectRef() != null) {
            ObjectReferenceType ref = varDef.getObjectRef();
            ref.setType(beans.prismContext.getSchemaRegistry().qualifyTypeName(ref.getType()));
            return addVariableDefinition(varDef.getName().getLocalPart(), ref);
        } else if (varDef.getValue() != null) {
            // This is raw value. We do have definition here. The best we can do is autodetect.
            // Expression evaluation code will do that as a fallback behavior.
            return addVariableDefinition(varDef.getName().getLocalPart(), varDef.getValue(), Object.class);
        } else {
            LOGGER.warn("Empty definition of variable {} in {}, ignoring it", varDef.getName(), getContextDescription());
            return typedThis();
        }
    }

    public RT addVariableDefinition(String name, ObjectReferenceType objectRef) {
        return addVariableDefinition(name, objectRef, objectRef.asReferenceValue().getDefinition());
    }

    public <O extends ObjectType> RT addVariableDefinition(String name, O objectType, Class<O> expectedClass) {
        // Maybe determine definition from schema registry here in case that object is null. We can do that here.
        variables.putObject(name, objectType, expectedClass);
        return typedThis();
    }

    public <O extends ObjectType> RT addVariableDefinition(String name, PrismObject<O> midpointObject, Class<O> expectedClass) {
        // Maybe determine definition from schema registry here in case that object is null. We can do that here.
        variables.putObject(name, midpointObject, expectedClass);
        return typedThis();
    }

    public RT addVariableDefinition(String name, String value) {
        MutablePrismPropertyDefinition<Object> def = beans.prismContext.definitionFactory().createPropertyDefinition(
                new QName(SchemaConstants.NS_C, name), PrimitiveType.STRING.getQname());
        return addVariableDefinition(name, value, def);
    }

    public RT addVariableDefinition(String name, Boolean value) {
        MutablePrismPropertyDefinition<Object> def = beans.prismContext.definitionFactory().createPropertyDefinition(
                new QName(SchemaConstants.NS_C, name), PrimitiveType.BOOLEAN.getQname());
        return addVariableDefinition(name, value, def);
    }

    public RT addVariableDefinition(String name, Integer value) {
        MutablePrismPropertyDefinition<Object> def = beans.prismContext.definitionFactory().createPropertyDefinition(
                new QName(SchemaConstants.NS_C, name), PrimitiveType.INT.getQname());
        return addVariableDefinition(name, value, def);
    }

    public RT addVariableDefinition(String name, PrismValue value) {
        return addVariableDefinition(name, value, value.getParent().getDefinition());
    }

    public RT addVariableDefinition(String name, ObjectDeltaObject<?> value) {
        if (value != null) {
            PrismObjectDefinition<?> definition = value.getDefinition();
            if (definition == null) {
                throw new IllegalArgumentException("Attempt to set variable '" + name + "' as ODO without a definition: " + value);
            }
            return addVariableDefinition(name, value, definition);
        } else {
            return addVariableDefinition(name, null, ObjectDeltaObject.class); // todo ok?
        }
    }

    // mainVariable of "null" means the default source
    public RT addAliasRegistration(String alias, @Nullable String mainVariable) {
        variables.registerAlias(alias, mainVariable);
        return typedThis();
    }

    public RT addVariableDefinitions(VariablesMap extraVariables) {
        variables.putAll(extraVariables);
        return typedThis();
    }

    public RT addVariableDefinition(String name, Object value, ItemDefinition definition) {
        variables.put(name, value, definition);
        return typedThis();
    }

    public RT addVariableDefinition(String name, Object value, Class<?> typeClass) {
        variables.put(name, value, typeClass);
        return typedThis();
    }

    public boolean hasVariableDefinition(String varName) {
        return variables.containsKey(varName);
    }

    public boolean isApplicableToChannel(String channel) {
        return MappingImpl.isApplicableToChannel(mappingBean, channel);
    }

    public RT additionalSource(Source<?, ?> source) {
        additionalSources.add(source);
        return typedThis();
    }

    public MappingStrengthType getStrength() {
        return MappingImpl.getStrength(mappingBean);
    }

    //region Plain getters
    public ModelCommonBeans getBeans() {
        return beans;
    }

    public VariablesMap getVariables() {
        return variables;
    }

    public MBT getMappingBean() {
        return mappingBean;
    }

    public MappingKindType getMappingKind() {
        return mappingKind;
    }

    public ItemPath getImplicitSourcePath() {
        return implicitSourcePath;
    }

    public ItemPath getImplicitTargetPath() {
        return implicitTargetPath;
    }

    public Source<?, ?> getDefaultSource() {
        return defaultSource;
    }

    public List<Source<?, ?>> getAdditionalSources() {
        return additionalSources;
    }

    public D getDefaultTargetDefinition() {
        return defaultTargetDefinition;
    }

    public ExpressionProfile getExpressionProfile() {
        return expressionProfile;
    }

    public ItemPath getDefaultTargetPath() {
        return defaultTargetPath;
    }

    public Collection<V> getOriginalTargetValues() {
        return originalTargetValues;
    }

    public ObjectDeltaObject<?> getSourceContext() {
        return sourceContext;
    }

    public PrismContainerDefinition<?> getTargetContext() {
        return targetContext;
    }

    public OriginType getOriginType() {
        return originType;
    }

    public ObjectType getOriginObject() {
        return originObject;
    }

    public ConfigurableValuePolicySupplier getValuePolicySupplier() {
        return valuePolicySupplier;
    }

    public VariableProducer getVariableProducer() {
        return variableProducer;
    }

    public MappingPreExpression getMappingPreExpression() {
        return mappingPreExpression;
    }

    public boolean isConditionMaskOld() {
        return conditionMaskOld;
    }

    public boolean isConditionMaskNew() {
        return conditionMaskNew;
    }

    public MappingSpecificationType getMappingSpecification() {
        return mappingSpecification;
    }

    public XMLGregorianCalendar getNow() {
        return now;
    }

    public XMLGregorianCalendar getDefaultReferenceTime() {
        return defaultReferenceTime;
    }

    public boolean isProfiling() {
        return profiling;
    }

    public String getContextDescription() {
        return contextDescription;
    }

    public QName getMappingQName() {
        return mappingQName;
    }

    public RefinedObjectClassDefinition getRefinedObjectClassDefinition() {
        return refinedObjectClassDefinition;
    }
    //endregion

    private RT typedThis() {
        //noinspection unchecked
        return (RT) this;
    }
}
