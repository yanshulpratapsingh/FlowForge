package com.flowforge.dto;

import com.flowforge.entity.Job;
import com.flowforge.enums.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class JobResponseTest {

    @Test
    public void testFromEntityMapsAllFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduled = now.plusMinutes(10);
        LocalDateTime lease = now.plusSeconds(30);
        LocalDateTime started = now.minusSeconds(5);
        LocalDateTime failed = now.minusSeconds(2);

        Job job = new Job();
        job.setId(id);
        job.setName("test-job");
        job.setPayload("{\"key\":\"value\"}");
        job.setPriority(7);
        job.setStatus(JobStatus.RUNNING);
        job.setRetryCount(2);
        job.setMaxRetries(5);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setScheduledAt(scheduled);
        job.setWorkerId("worker-123");
        job.setLeaseUntil(lease);
        job.setStartedAt(started);
        job.setLastErrorMessage("NullPointerException");
        job.setLastFailedAt(failed);
        job.setType("CUSTOM_TYPE");

        JobResponse response = JobResponse.from(job);

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("test-job", response.getName());
        assertEquals("{\"key\":\"value\"}", response.getPayload());
        assertEquals(7, response.getPriority());
        assertEquals(JobStatus.RUNNING, response.getStatus());
        assertEquals(2, response.getRetryCount());
        assertEquals(5, response.getMaxRetries());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
        assertEquals(scheduled, response.getScheduledAt());
        assertEquals("worker-123", response.getWorkerId());
        assertEquals(lease, response.getLeaseUntil());
        assertEquals(started, response.getStartedAt());
        assertEquals("NullPointerException", response.getLastErrorMessage());
        assertEquals(failed, response.getLastFailedAt());
        assertEquals("CUSTOM_TYPE", response.getType());
    }

    @Test
    public void testFromEntityHandlesNullableFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Job job = new Job();
        job.setId(id);
        job.setName("minimal-job");
        job.setPayload(null);
        job.setPriority(0);
        job.setStatus(JobStatus.CREATED);
        job.setRetryCount(0);
        job.setMaxRetries(3);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setScheduledAt(null);
        job.setWorkerId(null);
        job.setLeaseUntil(null);
        job.setStartedAt(null);
        job.setLastErrorMessage(null);
        job.setLastFailedAt(null);
        job.setType("SIMULATED");

        JobResponse response = JobResponse.from(job);

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("minimal-job", response.getName());
        assertNull(response.getPayload());
        assertEquals(0, response.getPriority());
        assertEquals(JobStatus.CREATED, response.getStatus());
        assertEquals(0, response.getRetryCount());
        assertEquals(3, response.getMaxRetries());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
        assertNull(response.getScheduledAt());
        assertNull(response.getWorkerId());
        assertNull(response.getLeaseUntil());
        assertNull(response.getStartedAt());
        assertNull(response.getLastErrorMessage());
        assertNull(response.getLastFailedAt());
        assertEquals("SIMULATED", response.getType());
    }

    @Test
    public void testFromNullReturnsNull() {
        assertNull(JobResponse.from(null));
    }

    @Test
    public void testSerializationParityWithFullData() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 30, 45, 123456000);
        LocalDateTime scheduled = now.plusMinutes(10);
        LocalDateTime lease = now.plusSeconds(30);
        LocalDateTime started = now.minusSeconds(5);
        LocalDateTime failed = now.minusSeconds(2);

        Job job = new Job();
        job.setId(id);
        job.setName("parity-job");
        job.setPayload("{\"data\":123}");
        job.setPriority(9);
        job.setStatus(JobStatus.RUNNING);
        job.setRetryCount(3);
        job.setMaxRetries(5);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setScheduledAt(scheduled);
        job.setWorkerId("worker-456");
        job.setLeaseUntil(lease);
        job.setStartedAt(started);
        job.setLastErrorMessage("Timeout error");
        job.setLastFailedAt(failed);
        job.setType("COMPUTE");

        JobResponse response = JobResponse.from(job);

        String entityJson = mapper.writeValueAsString(job);
        String responseJson = mapper.writeValueAsString(response);

        com.fasterxml.jackson.databind.JsonNode entityNode = mapper.readTree(entityJson);
        com.fasterxml.jackson.databind.JsonNode responseNode = mapper.readTree(responseJson);

        assertEquals(entityNode, responseNode, "Job entity and JobResponse must produce identical JSON trees");
    }

    @Test
    public void testSerializationParityWithNullFields() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 30, 45, 0);

        Job job = new Job();
        job.setId(id);
        job.setName("minimal-parity-job");
        job.setPayload(null);
        job.setPriority(0);
        job.setStatus(JobStatus.CREATED);
        job.setRetryCount(0);
        job.setMaxRetries(1);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setScheduledAt(null);
        job.setWorkerId(null);
        job.setLeaseUntil(null);
        job.setStartedAt(null);
        job.setLastErrorMessage(null);
        job.setLastFailedAt(null);
        job.setType("SIMULATED");

        JobResponse response = JobResponse.from(job);

        String entityJson = mapper.writeValueAsString(job);
        String responseJson = mapper.writeValueAsString(response);

        com.fasterxml.jackson.databind.JsonNode entityNode = mapper.readTree(entityJson);
        com.fasterxml.jackson.databind.JsonNode responseNode = mapper.readTree(responseJson);

        assertEquals(entityNode, responseNode, "Job entity and JobResponse must produce identical JSON trees with nulls");
    }
}
