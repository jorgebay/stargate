/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package io.stargate.db.datastore.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import io.stargate.db.datastore.query.ColumnOrder;
import io.stargate.db.datastore.query.ImmutableColumnOrder;
import io.stargate.db.datastore.query.ImmutableWhereCondition;
import io.stargate.db.datastore.query.WhereCondition;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableList;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableSet;

import static io.stargate.db.datastore.schema.Column.Kind.Clustering;
import static io.stargate.db.datastore.schema.Column.Kind.PartitionKey;
import static io.stargate.db.datastore.schema.Column.Kind.Regular;
import static io.stargate.db.datastore.schema.Column.Kind.Static;

/**
 * DseDB tables and materialized views are queried in roughly the same way. This class abstracts DseDB's map of
 * (partitionKey, clusteringKey) -> fields into an Expression based query.
 */
public abstract class AbstractTable implements Index, QualifiedSchemaEntity
{
    private static final long serialVersionUID = -5320339139947924742L;

    private static final Set<WhereCondition.Predicate> ALLOWED_PARTITION_KEY_PREDICATES = ImmutableSet.of(WhereCondition.Predicate.Eq, WhereCondition.Predicate.In);
    private static final Set<WhereCondition.Predicate> ALLOWED_CLUSTERING_COLUMN_PREDICATES = ImmutableSet.of(WhereCondition.Predicate.Eq, WhereCondition.Predicate.Gt,
            WhereCondition.Predicate.Gte, WhereCondition.Predicate.Lt,
            WhereCondition.Predicate.Lte, WhereCondition.Predicate.In);

    public abstract List<Column> columns();

    @Value.Lazy
    Map<String, Column> columnMap()
    {
        return columns().stream().collect(Collectors.toMap(Column::name,
                Function.identity()));
    }

    @Value.Lazy
    public List<Column> partitionKeyColumns()
    {
        return ImmutableList.copyOf(columns().stream().filter(c -> c.kind() == PartitionKey)
                .collect(Collectors.toList()));
    }

    // for clustering keys, order matters
    @Value.Lazy
    public List<Column> clusteringKeyColumns()
    {
        return ImmutableList.copyOf(columns().stream().filter(c -> c.kind() == Clustering)
                .collect(Collectors.toList()));
    }

    @Value.Lazy
    public List<Column> primaryKeyColumns()
    {
        return new ImmutableList.Builder<Column>().addAll(partitionKeyColumns()).addAll(clusteringKeyColumns()).build();
    }

    @Value.Lazy
    public List<Column> regularAndStaticColumns()
    {
        return ImmutableList.copyOf(columns().stream()
                .filter(c -> c.kind() == Regular || c.kind() == Static)
                .collect(Collectors.toList()));
    }

    @Value.Lazy
    public Set<Column> getRequiredIndexColumns()
    {
        return ImmutableSet.<Column>builder().addAll(partitionKeyColumns()).build();
    }

    @Value.Lazy
    public Set<Column> getOptionalIndexColumns()
    {
        return ImmutableSet.<Column>builder().addAll(clusteringKeyColumns()).build();
    }

    public Column column(String name)
    {
        if (Column.TTL.name().equals(name))
        {
            return Column.TTL;
        }
        return columnMap().get(name);
    }

    @Override
    public boolean supports(List<Column> select, List<WhereCondition<?>> conditions, List<ColumnOrder> orders,
                            OptionalLong limit)
    {
        // Dereference the columns. This allows us to do contains tests.
        select = select.stream().map(this::dereference).collect(Collectors.toList());
        conditions = conditions.stream()
                .map(c -> ImmutableWhereCondition.builder().from((WhereCondition<Object>) c)
                        .column(dereference(c.column())).build())
                .collect(Collectors.toList());
        orders = orders.stream().map(o -> ImmutableColumnOrder.of(dereference(o.column()), o.order()))
                .collect(Collectors.toList());

        if (conditions.isEmpty())
        {
            return orders.isEmpty();
        }

        if (!allSelectColumnsRecognised(select))
        {
            return false;
        }

        if (!allConditionColumnsRecognised(conditions))
        {
            return false;
        }

        if (!allPartitionKeysCovered(conditions))
        {
            return false;
        }

        if (!clusteringConditionsSupported(conditions))
        {
            return false;
        }

        return orderSupported(conditions, orders, false) || orderSupported(conditions, orders, true);
    }

    private boolean allPartitionKeysCovered(List<WhereCondition<?>> conditions)
    {
        return partitionKeyColumns().stream().allMatch(c -> conditions.stream()
                .anyMatch(p -> p.column().equals(c) && ALLOWED_PARTITION_KEY_PREDICATES.contains(p.predicate())));
    }

    private boolean allConditionColumnsRecognised(List<WhereCondition<?>> conditions)
    {
        return conditions.stream().allMatch(c -> primaryKeyColumns().contains(c.column()));
    }

    private boolean allSelectColumnsRecognised(List<Column> select)
    {
        return select.stream().allMatch(c -> columns().contains(c) || c == Column.STAR);
    }

    private boolean clusteringConditionsSupported(List<WhereCondition<?>> conditions)
    {
        List<WhereCondition> clusteringKeyRestrictions = conditions.stream()
                .filter(c -> clusteringKeyColumns().contains(c.column())).collect(Collectors.toList());

        List<WhereCondition> unusedClusteringKeyRestrictions = new ArrayList<>(clusteringKeyRestrictions);
        for (Column column : clusteringKeyColumns())
        {
            boolean found = unusedClusteringKeyRestrictions.removeIf(
                    c -> c.column().equals(column) && ALLOWED_CLUSTERING_COLUMN_PREDICATES.contains(c.predicate()));
            if (!found)
            {
                // As soon as we miss a clustering key restriction we have to stop
                break;
            }
        }

        // If we didn't manage to use all the clustering key restrictions then we can't query.
        return unusedClusteringKeyRestrictions.isEmpty();
    }

    private boolean orderSupported(List<WhereCondition<?>> conditions, List<ColumnOrder> orders, boolean reverse)
    {
        List<ColumnOrder> unusedOrders = new ArrayList<>(orders);
        for (Column column : clusteringKeyColumns())
        {
            if (conditions.stream().anyMatch(p -> p.column().equals(column) && p.predicate() == WhereCondition.Predicate.Eq))
            {
                // The order was covered by a condition so we can safely skip it
                unusedOrders.removeIf(o -> o.column().equals(column));
                continue;
            }
            Column.Order desiredOrder = reverse ? column.order().reversed() : column.order();
            boolean found = unusedOrders
                    .removeIf(o -> o.column().equals(column) && o.order() == desiredOrder);
            if (!found)
            {
                // As soon as we miss a clustering key order restriction we have to stop
                break;
            }
        }

        return unusedOrders.isEmpty();
    }

    private Column dereference(Column column)
    {
        Column dereferenced = column(column.name());
        return dereferenced != null ? dereferenced : column;
    }
}
