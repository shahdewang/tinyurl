package com.bufferstack.tinyurl.service;

import com.bufferstack.tinuyrl.jooq.Tables;
import com.bufferstack.tinuyrl.jooq.tables.records.UrlMappingRecord;
import com.bufferstack.tinyurl.exception.MappingNotFoundException;
import com.bufferstack.tinyurl.models.TinyUrlMapping;
import com.bufferstack.tinyurl.zookeeper.IdentifierStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Result;
import org.springframework.stereotype.Service;

@Service
public class UrlMappingService {

    private final DSLContext dslContext;
    private final IdentifierStream<String> identifierStream;
    private final Clock clock;

    public UrlMappingService(DSLContext dslContext, IdentifierStream<String> identifierStream, Clock clock) {
        this.dslContext = dslContext;
        this.identifierStream = identifierStream;
        this.clock = clock;
    }

    public TinyUrlMapping addLink(String fullUrl) {
        String code = identifierStream.next();
        OffsetDateTime createdAt =  OffsetDateTime.ofInstant(clock.instant(), clock.getZone());
        dslContext
                .insertInto(Tables.URL_MAPPING)
                .set(Tables.URL_MAPPING.CODE, code)
                .set(Tables.URL_MAPPING.FULL_URL, fullUrl)
                .set(Tables.URL_MAPPING.CREATED_AT, createdAt)
                .execute();
        return new TinyUrlMapping(code, fullUrl, createdAt.toInstant());
    }

    public TinyUrlMapping getLink(String code) {
        Record record = dslContext
                .select()
                .from(Tables.URL_MAPPING)
                .where(Tables.URL_MAPPING.CODE.eq(code))
                .fetchOne();
        if (record == null) {
            throw new MappingNotFoundException(code);
        }
        return new TinyUrlMapping(record.get(Tables.URL_MAPPING.CODE), record.get(Tables.URL_MAPPING.FULL_URL),
                record.get(Tables.URL_MAPPING.CREATED_AT).toInstant());
    }
}
