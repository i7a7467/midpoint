/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmapping;

import com.evolveum.midpoint.repo.sqale.qbean.MNode;
import com.evolveum.midpoint.repo.sqale.qmodel.QNode;
import com.evolveum.midpoint.repo.sqale.qmodel.QObject;
import com.evolveum.midpoint.repo.sqlbase.SqlRepoContext;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerContext;
import com.evolveum.midpoint.xml.ns._public.common.common_3.NodeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Mapping between {@link QObject} and {@link ObjectType}.
 */
public class QNodeMapping
        extends QObjectMapping<NodeType, QNode, MNode> {

    public static final String DEFAULT_ALIAS_NAME = "nod";

    public static final QNodeMapping INSTANCE = new QNodeMapping();

    private QNodeMapping() {
        super(QNode.TABLE_NAME, DEFAULT_ALIAS_NAME, NodeType.class, QNode.class);
    }

    @Override
    protected QNode newAliasInstance(String alias) {
        return new QNode(alias);
    }

    @Override
    public NodeSqlTransformer createTransformer(
            SqlTransformerContext transformerContext, SqlRepoContext sqlRepoContext) {
        return new NodeSqlTransformer(transformerContext, this);
    }

    @Override
    public MNode newRowObject() {
        return new MNode();
    }
}