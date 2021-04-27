/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.ref;

import java.util.function.BiFunction;

import com.querydsl.core.types.Predicate;

import com.evolveum.midpoint.prism.Referencable;
import com.evolveum.midpoint.repo.sqale.qmodel.QOwnedByMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.SqaleTableMapping;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerSupport;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;

/**
 * Base mapping between {@link QReference} subclasses and {@link ObjectReferenceType}.
 * See subtypes for mapping instances for specific tables and see {@link MReferenceType} as well.
 *
 * @param <Q> type of entity path for the reference table
 * @param <R> row type related to the {@link Q}
 * @param <OQ> query type of the reference owner
 * @param <OR> row type of the reference owner (related to {@link OQ})
 */
public class QReferenceMapping<Q extends QReference<R, OR>, R extends MReference, OQ extends FlexibleRelationalPathBase<OR>, OR>
        extends SqaleTableMapping<Referencable, Q, R>
        implements QOwnedByMapping<Referencable, R, OR> {

    // see also subtype specific alias names defined for instances below
    public static final String DEFAULT_ALIAS_NAME = "ref";

    /** Top level "abstract" reference table, not really needed for normal queries. */
    public static final QReferenceMapping<
            QReference<MReference, Object>, MReference, FlexibleRelationalPathBase<Object>, Object> INSTANCE =
            new QReferenceMapping<>(QReference.TABLE_NAME, DEFAULT_ALIAS_NAME, QReference.CLASS);

    protected QReferenceMapping(
            String tableName, String defaultAliasName, Class<Q> queryType) {
        super(tableName, defaultAliasName, Referencable.class, queryType);

        // TODO owner and reference type is not possible to query, probably OK
        //  not sure about this mapping yet, does it make sense to query ref components?
        /* REMOVE in 2022 if nothing is missing this
        addItemMapping(ObjectReferenceType.F_OID, uuidMapper(q -> q.targetOid));
        addItemMapping(ObjectReferenceType.F_TYPE,
                EnumItemFilterProcessor.mapper(q -> q.targetType));
        addItemMapping(ObjectReferenceType.F_RELATION,
                UriItemFilterProcessor.mapper(q -> q.relationId));
         */
    }

    @Override
    protected Q newAliasInstance(String alias) {
        //noinspection unchecked
        return (Q) new QReference<>(MReference.class, alias);
    }

    @Override
    public ReferenceSqlTransformer<Q, R, OR> createTransformer(
            SqlTransformerSupport transformerSupport) {
        return new ReferenceSqlTransformer<>(transformerSupport, this);
    }

    /** Defines a contract for creating the reference for the provided owner row. */
    public R newRowObject(OR ownerRow) {
        throw new UnsupportedOperationException(
                "Reference bean creation for owner row called on abstract reference mapping");
    }

    /** Returns a bi-function that constructs JOIN query predicate for owner and reference. */
    public BiFunction<OQ, Q, Predicate> joinOnPredicate() {
        throw new UnsupportedOperationException(
                "joinOnPredicate not supported on abstract reference mapping");
    }
}
