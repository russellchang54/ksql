/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License; you may not use this file
 * except in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.planner.plan;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.confluent.ksql.function.FunctionRegistry;
import io.confluent.ksql.parser.tree.Expression;
import io.confluent.ksql.query.QueryId;
import io.confluent.ksql.services.ServiceContext;
import io.confluent.ksql.structured.SchemaKStream;
import io.confluent.ksql.util.KafkaTopicClient;
import io.confluent.ksql.util.KsqlConfig;
import io.confluent.ksql.util.KsqlException;
import io.confluent.ksql.util.SelectExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.Immutable;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.streams.StreamsBuilder;

@Immutable
public class ProjectNode
    extends PlanNode {

  private final PlanNode source;
  private final Schema schema;
  private final Field keyField;
  private final List<Expression> projectExpressions;

  // TODO: pass in the "assignments" and the "outputs"
  // TODO: separately (i.e., get rid if the symbol := symbol idiom)
  @JsonCreator
  public ProjectNode(@JsonProperty("id") final PlanNodeId id,
                     @JsonProperty("source") final PlanNode source,
                     @JsonProperty("schema") final Schema schema,
                     @JsonProperty("projectExpressions")
                       final List<Expression> projectExpressions) {
    super(id, source.getNodeOutputType());

    requireNonNull(source, "source is null");
    requireNonNull(schema, "schema is null");
    requireNonNull(projectExpressions, "projectExpressions is null");

    this.source = source;
    this.schema = schema;
    this.keyField = source.getKeyField();
    this.projectExpressions = projectExpressions;
  }


  @Override
  public List<PlanNode> getSources() {
    return ImmutableList.of(source);
  }

  @JsonProperty
  public PlanNode getSource() {
    return source;
  }

  @Override
  public Schema getSchema() {
    return schema;
  }

  @Override
  protected int getPartitions(final KafkaTopicClient kafkaTopicClient) {
    return source.getPartitions(kafkaTopicClient);
  }

  @Override
  public Field getKeyField() {
    return keyField;
  }

  public List<SelectExpression> getProjectSelectExpressions() {
    if (schema.fields().size() != projectExpressions.size()) {
      throw new KsqlException("Error in projection. Schema fields and expression list are not "
                              + "compatible.");
    }

    final List<SelectExpression> selects = new ArrayList<>();
    for (int i = 0; i < projectExpressions.size(); i++) {
      selects.add(SelectExpression.of(schema.fields().get(i).name(), projectExpressions.get(i)));
    }
    return selects;
  }

  @Override
  public <C, R> R accept(final PlanVisitor<C, R> visitor, final C context) {
    return visitor.visitProject(this, context);
  }

  @Override
  public SchemaKStream<?> buildStream(
      final StreamsBuilder builder,
      final KsqlConfig ksqlConfig,
      final ServiceContext serviceContext,
      final FunctionRegistry functionRegistry,
      final Map<String, Object> props,
      final QueryId queryId) {
    return getSource().buildStream(
        builder,
        ksqlConfig,
        serviceContext, 
        functionRegistry,
        props,
        queryId
    ).select(getProjectSelectExpressions());
  }
}
