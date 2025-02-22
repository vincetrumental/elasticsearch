/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.vectortile.rest;

import com.carrotsearch.randomizedtesting.generators.RandomPicks;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.core.CheckedConsumer;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.rest.FakeRestRequest;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;

public class VectorTileRequestTests extends ESTestCase {

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        SearchModule searchModule = new SearchModule(Settings.EMPTY, emptyList());
        return new NamedXContentRegistry(searchModule.getNamedXContents());
    }

    public void testDefaults() throws IOException {
        assertRestRequest((builder) -> {}, (vectorTileRequest) -> {
            assertThat(vectorTileRequest.getSize(), Matchers.equalTo(VectorTileRequest.Defaults.SIZE));
            assertThat(vectorTileRequest.getExtent(), Matchers.equalTo(VectorTileRequest.Defaults.EXTENT));
            assertThat(vectorTileRequest.getAggBuilder(), Matchers.equalTo(VectorTileRequest.Defaults.AGGS));
            assertThat(vectorTileRequest.getFieldAndFormats(), Matchers.equalTo(VectorTileRequest.Defaults.FETCH));
            assertThat(vectorTileRequest.getGridType(), Matchers.equalTo(VectorTileRequest.Defaults.GRID_TYPE));
            assertThat(vectorTileRequest.getGridPrecision(), Matchers.equalTo(VectorTileRequest.Defaults.GRID_PRECISION));
            assertThat(vectorTileRequest.getExactBounds(), Matchers.equalTo(VectorTileRequest.Defaults.EXACT_BOUNDS));
            assertThat(vectorTileRequest.getRuntimeMappings(), Matchers.equalTo(VectorTileRequest.Defaults.RUNTIME_MAPPINGS));
            assertThat(vectorTileRequest.getSortBuilders(), Matchers.equalTo(VectorTileRequest.Defaults.SORT));
            assertThat(vectorTileRequest.getQueryBuilder(), Matchers.equalTo(VectorTileRequest.Defaults.QUERY));
        });
    }

    public void testFieldSize() throws IOException {
        final int size = randomIntBetween(0, 10000);
        assertRestRequest(
            (builder) -> { builder.field(SearchSourceBuilder.SIZE_FIELD.getPreferredName(), size); },
            (vectorTileRequest) -> { assertThat(vectorTileRequest.getSize(), Matchers.equalTo(size)); }
        );
    }

    public void testFieldExtent() throws IOException {
        final int extent = randomIntBetween(256, 8192);
        assertRestRequest(
            (builder) -> { builder.field(VectorTileRequest.EXTENT_FIELD.getPreferredName(), extent); },
            (vectorTileRequest) -> { assertThat(vectorTileRequest.getExtent(), Matchers.equalTo(extent)); }
        );
    }

    public void testFieldFetch() throws IOException {
        final String fetchField = randomAlphaOfLength(10);
        assertRestRequest(
            (builder) -> { builder.field(SearchSourceBuilder.FETCH_FIELDS_FIELD.getPreferredName(), new String[] { fetchField }); },
            (vectorTileRequest) -> {
                assertThat(vectorTileRequest.getFieldAndFormats(), Matchers.iterableWithSize(1));
                assertThat(vectorTileRequest.getFieldAndFormats().get(0).field, Matchers.equalTo(fetchField));
            }
        );
    }

    public void testFieldGridType() throws IOException {
        final VectorTileRequest.GRID_TYPE grid_type = RandomPicks.randomFrom(random(), VectorTileRequest.GRID_TYPE.values());
        assertRestRequest(
            (builder) -> { builder.field(VectorTileRequest.GRID_TYPE_FIELD.getPreferredName(), grid_type.name()); },
            (vectorTileRequest) -> { assertThat(vectorTileRequest.getGridType(), Matchers.equalTo(grid_type)); }
        );
    }

    public void testFieldGridPrecision() throws IOException {
        final int grid_precision = randomIntBetween(1, 8);
        assertRestRequest(
            (builder) -> { builder.field(VectorTileRequest.GRID_PRECISION_FIELD.getPreferredName(), grid_precision); },
            (vectorTileRequest) -> { assertThat(vectorTileRequest.getGridPrecision(), Matchers.equalTo(grid_precision)); }
        );
    }

    public void testFieldExactBounds() throws IOException {
        final boolean exactBounds = randomBoolean();
        assertRestRequest(
            (builder) -> { builder.field(VectorTileRequest.EXACT_BOUNDS_FIELD.getPreferredName(), exactBounds); },
            (vectorTileRequest) -> { assertThat(vectorTileRequest.getExactBounds(), Matchers.equalTo(exactBounds)); }
        );
    }

    public void testFieldQuery() throws IOException {
        final QueryBuilder queryBuilder = new TermQueryBuilder(randomAlphaOfLength(10), randomAlphaOfLength(10));
        assertRestRequest((builder) -> {
            builder.field(SearchSourceBuilder.QUERY_FIELD.getPreferredName());
            queryBuilder.toXContent(builder, ToXContent.EMPTY_PARAMS);
        }, (vectorTileRequest) -> { assertThat(vectorTileRequest.getQueryBuilder(), Matchers.equalTo(queryBuilder)); });
    }

    public void testFieldAgg() throws IOException {
        final AggregationBuilder aggregationBuilder = new AvgAggregationBuilder("xxx").field("xxxx");
        assertRestRequest((builder) -> {
            builder.startObject(SearchSourceBuilder.AGGS_FIELD.getPreferredName());
            aggregationBuilder.toXContent(builder, ToXContent.EMPTY_PARAMS);
            builder.endObject();
        }, (vectorTileRequest) -> {
            assertThat(vectorTileRequest.getAggBuilder().getAggregatorFactories(), Matchers.iterableWithSize(1));
            assertThat(vectorTileRequest.getAggBuilder().getAggregatorFactories().contains(aggregationBuilder), Matchers.equalTo(true));
        });
    }

    public void testFieldRuntimeMappings() throws IOException {
        final String fieldName = randomAlphaOfLength(10);
        assertRestRequest((builder) -> {
            builder.startObject(SearchSourceBuilder.RUNTIME_MAPPINGS_FIELD.getPreferredName())
                .startObject(fieldName)
                .field("script", "emit('foo')")
                .field("type", "string")
                .endObject()
                .endObject();
        }, (vectorTileRequest) -> {
            assertThat(vectorTileRequest.getRuntimeMappings(), Matchers.aMapWithSize(1));
            assertThat(vectorTileRequest.getRuntimeMappings().get(fieldName), Matchers.notNullValue());
        });
    }

    public void testFieldSort() throws IOException {
        final String sortName = randomAlphaOfLength(10);
        assertRestRequest(
            (builder) -> {
                builder.startArray(SearchSourceBuilder.SORT_FIELD.getPreferredName())
                    .startObject()
                    .field(sortName, "desc")
                    .endObject()
                    .endArray();
            },
            (vectorTileRequest) -> {
                assertThat(vectorTileRequest.getSortBuilders(), Matchers.iterableWithSize(1));
                FieldSortBuilder sortBuilder = (FieldSortBuilder) vectorTileRequest.getSortBuilders().get(0);
                assertThat(sortBuilder.getFieldName(), Matchers.equalTo(sortName));
            }
        );
    }

    @AwaitsFix(bugUrl = "https://github.com/elastic/elasticsearch/issues/74338")
    public void testWrongTile() {
        final int z = randomIntBetween(1, 10);
        final int x = -randomIntBetween(0, (1 << z) - 1);
        final int y = -randomIntBetween(0, (1 << z) - 1);
        final String index = randomAlphaOfLength(10);
        final String field = randomAlphaOfLength(10);
        final FakeRestRequest request = getBasicRequestBuilder(index, field, z, x, y).build();
        final IllegalArgumentException ex = expectThrows(IllegalArgumentException.class, () -> VectorTileRequest.parseRestRequest(request));
        assertThat(ex.getMessage(), Matchers.equalTo("Zoom/X/Y combination is not valid: " + z + "/" + x + "/" + y));
    }

    private void assertRestRequest(CheckedConsumer<XContentBuilder, IOException> consumer, Consumer<VectorTileRequest> asserter)
        throws IOException {
        final int z = randomIntBetween(1, 10);
        final int x = randomIntBetween(0, (1 << z) - 1);
        final int y = randomIntBetween(0, (1 << z) - 1);
        final String index = randomAlphaOfLength(10);
        final String field = randomAlphaOfLength(10);
        final FakeRestRequest.Builder requestBuilder = getBasicRequestBuilder(index, field, z, x, y);
        final XContentBuilder builder = JsonXContent.contentBuilder();
        builder.startObject();
        consumer.accept(builder);
        builder.endObject();
        final FakeRestRequest request = requestBuilder.withContent(BytesReference.bytes(builder), builder.contentType()).build();
        final VectorTileRequest vectorTileRequest = VectorTileRequest.parseRestRequest(request);
        assertThat(vectorTileRequest.getIndexes(), Matchers.equalTo(new String[] { index }));
        assertThat(vectorTileRequest.getField(), Matchers.equalTo(field));
        assertThat(vectorTileRequest.getZ(), Matchers.equalTo(z));
        assertThat(vectorTileRequest.getX(), Matchers.equalTo(x));
        assertThat(vectorTileRequest.getY(), Matchers.equalTo(y));
        asserter.accept(vectorTileRequest);
    }

    private FakeRestRequest.Builder getBasicRequestBuilder(String index, String field, int z, int x, int y) {
        return new FakeRestRequest.Builder(xContentRegistry()).withMethod(RestRequest.Method.GET)
            .withParams(
                Map.of(
                    VectorTileRequest.INDEX_PARAM,
                    index,
                    VectorTileRequest.FIELD_PARAM,
                    field,
                    VectorTileRequest.Z_PARAM,
                    "" + z,
                    VectorTileRequest.X_PARAM,
                    "" + x,
                    VectorTileRequest.Y_PARAM,
                    "" + y
                )
            );
    }
}
