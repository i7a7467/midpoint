/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.delta.item;

import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.repo.sqale.SqaleUpdateContext;
import com.evolveum.midpoint.repo.sqale.delta.ItemDeltaProcessor;

/**
 * Applies item delta values to an item and arranges necessary SQL changes using update context.
 * This typically means adding set clauses to the update but can also mean adding rows
 * for containers, etc.
 * This kind of item delta processor does not resolve multi-part item paths, see other
 * subclasses of {@link ItemDeltaProcessor} for that.
 *
 * @param <T> expected type of the real value for the modification (after optional conversion)
 */
public abstract class ItemDeltaValueProcessor<T> implements ItemDeltaProcessor {

    protected final SqaleUpdateContext<?, ?, ?> context;

    protected ItemDeltaValueProcessor(SqaleUpdateContext<?, ?, ?> context) {
        this.context = context;
    }

    /**
     * Often the single real value is necessary, optionally transformed using
     * {@link #transformRealValue(Object)} to get expected type.
     * Either method can be overridden or not used at all depending on the complexity
     * of the concrete delta processor.
     */
    protected T getAnyValue(ItemDelta<?, ?> modification) {
        PrismValue anyValue = modification.getAnyValue();
        return anyValue != null ? transformRealValue(anyValue.getRealValue()) : null;
    }

    protected T transformRealValue(Object realValue) {
        //noinspection unchecked
        return (T) realValue;
    }

    /** Sets the database columns to reflect the provided real value. */
    public void setRealValue(Object realValue) {
        setValue(transformRealValue(realValue));
    }

    /** Sets the database columns to reflect the provided value (must be transformed if needed). */
    public abstract void setValue(T value);

    /** Resets the database columns, exposed for the needs of container processing. */
    public abstract void delete();
}
