package com.bufferstack.tinyurl.service;

import com.bufferstack.tinuyrl.jooq.Tables;
import com.bufferstack.tinuyrl.jooq.tables.UrlMapping;
import com.bufferstack.tinuyrl.jooq.tables.records.UrlMappingRecord;
import com.bufferstack.tinyurl.generator.IdentifierStream;
import com.bufferstack.tinyurl.models.TinyUrlMapping;
import com.bufferstack.tinyurl.utils.TestUtils;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.InsertSetStep;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.TableField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class UrlMappingServiceTest {

    private final DSLContext dslContext = mock(DSLContext.class);
    private final IdentifierStream<String> identifierStream = mock(IdentifierStream.class);
    private final InsertSetStep<UrlMappingRecord> insertSetStep = mock(InsertSetStep.class);
    private final InsertSetMoreStep<UrlMappingRecord> insertSetMoreStep = mock(InsertSetMoreStep.class);
    private final SelectSelectStep<Record> selectSelectStep = mock(SelectSelectStep.class);
    private final SelectJoinStep<Record> selectJoinStep = mock(SelectJoinStep.class);
    private final SelectConditionStep<Record> selectConditionStep = mock(SelectConditionStep.class);
    private final Record record = mock(Record.class);

    private final Clock clock = TestUtils.clock();

    private final UrlMappingService urlMappingService = new UrlMappingService(dslContext, identifierStream, clock);

    @Test
    public void shouldAddLink() {
        String code = "14568";
        String url = "https://www.google.com";
        when(identifierStream.next()).thenReturn(code);
        when(dslContext.insertInto(eq(Tables.URL_MAPPING))).thenReturn(insertSetStep);
        when(insertSetStep.set(any(TableField.class), any(Object.class))).thenReturn(insertSetMoreStep);
        when(insertSetMoreStep.set(any(TableField.class), any(Object.class))).thenReturn(insertSetMoreStep);

        TinyUrlMapping link = urlMappingService.addLink(url);
        assertEquals(code, link.getCode());
        assertEquals(url, link.getFullUrl());
        assertNotNull(link.getCreatedAt());
    }

    @Test
    public void shouldGetLink() {
        String code = "14568";
        String url = "https://www.google.com";
        when(dslContext.select()).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(UrlMapping.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(eq(Tables.URL_MAPPING.CODE.eq(code)))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(record);
        when(record.get(eq(Tables.URL_MAPPING.CODE))).thenReturn(code);
        when(record.get(eq(Tables.URL_MAPPING.FULL_URL))).thenReturn(url);
        when(record.get(eq(Tables.URL_MAPPING.CREATED_AT))).thenReturn(OffsetDateTime.ofInstant(clock.instant(), clock.getZone()));

        TinyUrlMapping link = urlMappingService.getLink(code);
        assertEquals(code, link.getCode());
        assertEquals(url, link.getFullUrl());
        assertNotNull(link.getCreatedAt());
    }
}
