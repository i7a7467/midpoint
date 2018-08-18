/*
 * Copyright (c) 2010-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.component.assignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.web.component.prism.*;
import com.evolveum.midpoint.web.page.admin.PageAdminFocus;
import com.evolveum.midpoint.web.page.admin.users.component.AssignmentInfoDto;
import com.evolveum.midpoint.web.page.admin.users.component.AllAssignmentsPreviewDialog;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.evolveum.midpoint.gui.api.component.TypedAssignablePanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.form.Form;
import com.evolveum.midpoint.web.component.input.DropDownChoicePanel;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage.TableId;

/**
 * Created by honchar.
 */
public class AbstractRoleAssignmentPanel extends AssignmentPanel {

    private static final long serialVersionUID = 1L;

    private static final String ID_RELATION = "relation";
    private static final String ID_RELATION_CONTAINER = "relationContainer";
    private static final String ID_SHOW_ALL_ASSIGNMENTS_BUTTON = "showAllAssignmentsButton";

    protected static final String DOT_CLASS = AbstractRoleAssignmentPanel.class.getName() + ".";
    private static final String OPERATION_LOAD_TARGET_REF_OBJECT = DOT_CLASS + "loadAssignmentTargetRefObject";

    private RelationTypes relationValue = null;

    public AbstractRoleAssignmentPanel(String id, IModel<ContainerWrapper<AssignmentType>> assignmentContainerWrapperModel){
        super(id, assignmentContainerWrapperModel);
    }

    protected void initCustomLayout(WebMarkupContainer assignmentsContainer){

        WebMarkupContainer relationContainer = new WebMarkupContainer(ID_RELATION_CONTAINER);
        relationContainer.setOutputMarkupId(true);
        relationContainer.add(new VisibleEnableBehaviour() {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                return AbstractRoleAssignmentPanel.this.isRelationVisible();
            }

        });
        assignmentsContainer.addOrReplace(relationContainer);

        DropDownChoicePanel<RelationTypes> relation = WebComponentUtil.createEnumPanel(RelationTypes.class, ID_RELATION,
                WebComponentUtil.createReadonlyModelFromEnum(RelationTypes.class),
                new IModel<RelationTypes>() {
                    @Override
                    public RelationTypes getObject() {
                        return relationValue;
                    }

                    @Override
                    public void setObject(RelationTypes relationTypes) {
                        relationValue = relationTypes;
                    }

                    @Override
                    public void detach() {

                    }
                }, this, true,
                createStringResource("RelationTypes.ANY").getString());
        relation.getBaseFormComponent().add(new AjaxFormComponentUpdatingBehavior("change") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                refreshTable(target);
            }
        });
        relation.setOutputMarkupId(true);
        relation.setOutputMarkupPlaceholderTag(true);
        relationContainer.addOrReplace(relation);

        AjaxButton showAllAssignmentsButton = new AjaxButton(ID_SHOW_ALL_ASSIGNMENTS_BUTTON,
                createStringResource("AssignmentTablePanel.menu.showAllAssignments")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget ajaxRequestTarget) {
                showAllAssignments(ajaxRequestTarget);
            }
        };
        assignmentsContainer.addOrReplace(showAllAssignmentsButton);
        showAllAssignmentsButton.setOutputMarkupId(true);
        showAllAssignmentsButton.add(new VisibleEnableBehaviour(){

            private static final long serialVersionUID = 1L;

            public boolean isVisible(){
                return showAllAssignmentsVisible();
            }
        });
    }

    private DropDownChoicePanel<RelationTypes> getRelationPanel() {
        return (DropDownChoicePanel<RelationTypes>) getAssignmentContainer().get(ID_RELATION_CONTAINER).get(ID_RELATION);
    }


    protected void showAllAssignments(AjaxRequestTarget target) {
        PageBase pageBase = getPageBase();
        List<AssignmentInfoDto> previewAssignmentsList;
        if (pageBase instanceof PageAdminFocus) {
            previewAssignmentsList = ((PageAdminFocus<?>) pageBase).showAllAssignmentsPerformed(target);
        } else {
            previewAssignmentsList = Collections.emptyList();
        }
        AllAssignmentsPreviewDialog assignmentsDialog = new AllAssignmentsPreviewDialog(pageBase.getMainPopupBodyId(), previewAssignmentsList,
                pageBase);
        pageBase.showMainPopup(assignmentsDialog, target);
    }

    @Override
    protected void newAssignmentClickPerformed(AjaxRequestTarget target) {
        TypedAssignablePanel panel = new TypedAssignablePanel(
                getPageBase().getMainPopupBodyId(), getDefaultNewAssignmentFocusType()) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void addPerformed(AjaxRequestTarget target, List selected, QName relation, ShadowKindType kind, String intent) {
                super.addPerformed(target, selected, relation, kind, intent);
                addSelectedAssignmentsPerformed(target, selected, relation, kind, intent);
            }

            @Override
            protected List<ObjectTypes> getObjectTypesList(){
                return AbstractRoleAssignmentPanel.this.getObjectTypesList();
            }

        };
        panel.setOutputMarkupId(true);
        getPageBase().showMainPopup(panel, target);
    }

    protected Class getDefaultNewAssignmentFocusType(){
        return RoleType.class;
    }

    protected <T extends ObjectType> void addSelectedAssignmentsPerformed(AjaxRequestTarget target, List<T> assignmentsList, QName relation,
                                                                          ShadowKindType kind, String intent){
        if (assignmentsList == null || assignmentsList.isEmpty()){
            warn(getParentPage().getString("AssignmentTablePanel.message.noAssignmentSelected"));
            target.add(getPageBase().getFeedbackPanel());
            return;
        }

        int assignmentsLimit = AssignmentsUtil.loadAssignmentsLimit(new OperationResult(OPERATION_LOAD_ASSIGNMENTS_LIMIT),
                getPageBase());
        int addedAssignmentsCount = getNewAssignmentsCount() + assignmentsList.size();
        if (assignmentsLimit >= 0 && addedAssignmentsCount > assignmentsLimit) {
            warn(getParentPage().getString("AssignmentPanel.assignmentsLimitReachedWarning", assignmentsLimit));
            target.add(getPageBase().getFeedbackPanel());
            return;
        }

        for (T object : assignmentsList){
            PrismContainerDefinition<AssignmentType> definition = getModelObject().getItem().getDefinition();
            PrismContainerValue<AssignmentType> newAssignment;
            try {
                newAssignment = definition.instantiate().createNewValue();
                ObjectReferenceType ref = ObjectTypeUtil.createObjectRef(object, relation);
                AssignmentType assignmentType = newAssignment.asContainerable();
                if (ResourceType.class.equals(object.getClass())) {
                    ConstructionType constructionType = new ConstructionType();
                    constructionType.setResourceRef(ref);
                    constructionType.setKind(kind);
                    constructionType.setIntent(intent);
                    assignmentType.setConstruction(constructionType);
                } else {
                    assignmentType.setTargetRef(ref);
                }
                createNewAssignmentContainerValueWrapper(newAssignment);
                refreshTable(target);
                reloadSavePreviewButtons(target);
            } catch (SchemaException e) {
                getSession().error("Cannot create new assignment " + e.getMessage());
                target.add(getPageBase().getFeedbackPanel());
                target.add(this);
            }

        }


    }
    protected <T extends ObjectType> void addSelectedAssignmentsPerformed(AjaxRequestTarget target, List<T> assignmentsList, QName relation) {
        if (assignmentsList == null || assignmentsList.isEmpty()) {
            warn(getParentPage().getString("AssignmentTablePanel.message.noAssignmentSelected"));
            target.add(getPageBase().getFeedbackPanel());
            return;
        }
        int assignmentsLimit = AssignmentsUtil.loadAssignmentsLimit(new OperationResult(OPERATION_LOAD_ASSIGNMENTS_LIMIT),
                getPageBase());
        int addedAssignmentsCount = getNewAssignmentsCount() + assignmentsList.size();
        if (assignmentsLimit >= 0 && addedAssignmentsCount > assignmentsLimit) {
            warn(getParentPage().getString("AssignmentPanel.assignmentsLimitReachedWarning", assignmentsLimit));
            target.add(getPageBase().getFeedbackPanel());
            return;
        }

        for (T object : assignmentsList) {
            PrismContainerValue<AssignmentType> newAssignment = getModelObject().getItem().createNewValue();
            ObjectReferenceType ref = ObjectTypeUtil.createObjectRef(object, relation);
            AssignmentType assignmentType = newAssignment.asContainerable();
            if (ResourceType.class.equals(object.getClass())) {
                ConstructionType constructionType = new ConstructionType();
                constructionType.setResourceRef(ref);
                assignmentType.setConstruction(constructionType);
            } else {
                assignmentType.setTargetRef(ref);
            }
            createNewAssignmentContainerValueWrapper(newAssignment);
        }

        refreshTable(target);
        target.add(getAssignmentContainer());
        reloadSavePreviewButtons(target);
    }

    protected List<IColumn<ContainerValueWrapper<AssignmentType>, String>> initColumns() {
        List<IColumn<ContainerValueWrapper<AssignmentType>, String>> columns = new ArrayList<>();

        columns.add(new AbstractColumn<ContainerValueWrapper<AssignmentType>, String>(createStringResource("ObjectReferenceType.relation")) {
            @Override
            public void populateItem(Item<ICellPopulator<ContainerValueWrapper<AssignmentType>>> item, String componentId, IModel<ContainerValueWrapper<AssignmentType>> assignmentModel) {
                item.add(new Label(componentId, getRelationLabelValue(assignmentModel.getObject())));
            }
        });

        columns.add(new AbstractColumn<ContainerValueWrapper<AssignmentType>, String>(createStringResource("AssignmentType.tenant")){
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<ContainerValueWrapper<AssignmentType>>> item, String componentId,
                                     final IModel<ContainerValueWrapper<AssignmentType>> rowModel) {
                item.add(new Label(componentId, getTenantLabelModel(rowModel.getObject())));
            }
        });
        columns.add(new AbstractColumn<ContainerValueWrapper<AssignmentType>, String>(createStringResource("AssignmentType.orgReferenceShorten")){
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<ContainerValueWrapper<AssignmentType>>> item, String componentId,
                                     final IModel<ContainerValueWrapper<AssignmentType>> rowModel) {
                item.add(new Label(componentId, getOrgRefLabelModel(rowModel.getObject())));
            }
        });
        columns.add(new AbstractColumn<ContainerValueWrapper<AssignmentType>, String>(createStringResource("AbstractRoleAssignmentPanel.identifierLabel")){
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<ContainerValueWrapper<AssignmentType>>> item, String componentId,
                                     final IModel<ContainerValueWrapper<AssignmentType>> rowModel) {
                item.add(new Label(componentId, getIdentifierLabelModel(rowModel.getObject())));
            }
        });

        return columns;
    }

    private String getRelationLabelValue(ContainerValueWrapper<AssignmentType> assignmentWrapper){
        if (assignmentWrapper == null || assignmentWrapper.getContainerValue() == null || assignmentWrapper.getContainerValue().getValue() == null
                || assignmentWrapper.getContainerValue().getValue().getTargetRef() == null
                || assignmentWrapper.getContainerValue().getValue().getTargetRef().getRelation() == null){
            return "";
        }
        return assignmentWrapper.getContainerValue().getValue().getTargetRef().getRelation().getLocalPart();
    }

    protected boolean showAllAssignmentsVisible(){
        return true;
    }

    private VisibleEnableBehaviour visibleIfRoleBehavior(IModel<AssignmentEditorDto> assignmentModel){
        return new VisibleEnableBehaviour(){
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible(){
                return AssignmentEditorDtoType.ROLE.equals(assignmentModel.getObject().getType());
            }
        };
    }

    protected void initPaging(){
        getAssignmentsStorage().setPaging(ObjectPaging.createPaging(0, (int) getParentPage().getItemsPerPage(UserProfileStorage.TableId.ASSIGNMENTS_TAB_TABLE)));
    }

    @Override
    protected TableId getTableId() {
        return UserProfileStorage.TableId.ASSIGNMENTS_TAB_TABLE;
    }

    @Override
    protected int getItemsPerPage() {
        return (int) getParentPage().getItemsPerPage(UserProfileStorage.TableId.ASSIGNMENTS_TAB_TABLE);
    }

    protected ObjectQuery createObjectQuery() {
        QName relation = getRelation();
        if (PrismConstants.Q_ANY.equals(relation)){
            return QueryBuilder.queryFor(AssignmentType.class, getParentPage().getPrismContext())
                    .block()
                    .not()
                    .item(new ItemPath(AssignmentType.F_CONSTRUCTION, ConstructionType.F_RESOURCE_REF))
                    .isNull()
                    .endBlock()
                    .or()
                    .block()
                    .not()
                    .item(new ItemPath(AssignmentType.F_TARGET_REF))
                    .ref(SchemaConstants.ORG_DEPUTY)
                    .endBlock()
                    .and()
                    .not()
                    .exists(AssignmentType.F_POLICY_RULE)
                    .build();
        } else {
            return QueryBuilder.queryFor(AssignmentType.class, getParentPage().getPrismContext())
                    .item(new ItemPath(AssignmentType.F_TARGET_REF))
                    .ref(relation)
                    .build();
        }
    }

    private QName getRelation() {
        return relationValue == null ? PrismConstants.Q_ANY : relationValue.getRelation();
    }

    @Override
    protected AbstractAssignmentDetailsPanel createDetailsPanel(String idAssignmentDetails, Form<?> form, IModel<ContainerValueWrapper<AssignmentType>> model) {
        return new AbstractRoleAssignmentDetailsPanel(ID_ASSIGNMENT_DETAILS, form, model);
    }

    private IModel<String> getTenantLabelModel(ContainerValueWrapper<AssignmentType> assignmentContainer){
        if (assignmentContainer == null || assignmentContainer.getContainerValue() == null){
            return Model.of("");
        }
        AssignmentType assignment = assignmentContainer.getContainerValue().asContainerable();
        return Model.of(WebComponentUtil.getReferencedObjectDisplayNamesAndNames(Arrays.asList(assignment.getTenantRef()), false));

    }

    private IModel<String> getOrgRefLabelModel(ContainerValueWrapper<AssignmentType> assignmentContainer){
        if (assignmentContainer == null || assignmentContainer.getContainerValue() == null){
            return Model.of("");
        }
        AssignmentType assignment = assignmentContainer.getContainerValue().asContainerable();
        return Model.of(WebComponentUtil.getReferencedObjectDisplayNamesAndNames(Arrays.asList(assignment.getOrgRef()), false));

    }

    private <O extends ObjectType> IModel<String> getIdentifierLabelModel(ContainerValueWrapper<AssignmentType> assignmentContainer){
        if (assignmentContainer == null || assignmentContainer.getContainerValue() == null){
            return Model.of("");
        }
        AssignmentType assignment = assignmentContainer.getContainerValue().asContainerable();
        if (assignment.getTargetRef() == null){
            return Model.of("");
        }

        PrismObject<O> object = WebModelServiceUtils.loadObject(assignment.getTargetRef(), getPageBase(),
                getPageBase().createSimpleTask(OPERATION_LOAD_TARGET_REF_OBJECT), new OperationResult(OPERATION_LOAD_TARGET_REF_OBJECT));
        if (object == null || !(object.asObjectable() instanceof AbstractRoleType)){
            return Model.of("");
        }
        AbstractRoleType targetRefObject = (AbstractRoleType) object.asObjectable();
        if (StringUtils.isNotEmpty(targetRefObject.getIdentifier())){
            return Model.of(targetRefObject.getIdentifier());
        }
        if (targetRefObject.getDisplayName() != null && !targetRefObject.getName().getOrig().equals(targetRefObject.getDisplayName().getOrig())){
            return Model.of(targetRefObject.getName().getOrig());
        }
        return Model.of("");
    }

    protected boolean isRelationVisible() {
        return true;
    }

    protected List<ObjectTypes> getObjectTypesList(){
        return WebComponentUtil.createAssignableTypesList();
    }
}
