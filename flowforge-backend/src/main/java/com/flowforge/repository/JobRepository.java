package com.flowforge.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowforge.entity.Job;

import org.springframework.data.jpa.repository.Modifying;

public interface JobRepository extends JpaRepository<Job, UUID> {

    @Query(value = "SELECT * FROM jobs " +
                   "WHERE (status = 'QUEUED' OR status = 'RETRYING') " +
                   "  AND (scheduled_at IS NULL OR scheduled_at <= :now) " +
                   "ORDER BY " +
                   "  CASE WHEN EXTRACT(EPOCH FROM (:now - COALESCE(scheduled_at, created_at))) > :starvationThresholdSeconds THEN 1 ELSE 0 END DESC, " +
                   "  priority DESC, " +
                   "  COALESCE(scheduled_at, created_at) ASC, " +
                   "  id ASC " +
                   "LIMIT :limit " +
                   "FOR UPDATE SKIP LOCKED", 
           nativeQuery = true)
    List<Job> findExecutableJobsWithLock(
            @Param("now") LocalDateTime now, 
            @Param("limit") int limit,
            @Param("starvationThresholdSeconds") long starvationThresholdSeconds);

    @Modifying
    @Query("UPDATE Job j SET j.leaseUntil = :newLeaseUntil, j.updatedAt = :now " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId AND j.leaseUntil > :now")
    int extendLease(@Param("id") UUID id, 
                    @Param("workerId") String workerId, 
                    @Param("newLeaseUntil") LocalDateTime newLeaseUntil, 
                    @Param("now") LocalDateTime now);

    @Query(value = "SELECT * FROM jobs " +
                   "WHERE status = 'RUNNING' AND lease_until < :now " +
                   "FOR UPDATE SKIP LOCKED", 
           nativeQuery = true)
    List<Job> findExpiredJobsWithLock(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Job j SET j.status = 'COMPLETED', j.updatedAt = :now " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId")
    int markCompleted(@Param("id") UUID id, 
                      @Param("workerId") String workerId, 
                      @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Job j SET j.status = 'RETRYING', j.retryCount = :retryCount, j.scheduledAt = :scheduledAt, " +
           "j.lastErrorMessage = :errorMessage, j.lastFailedAt = :failedAt, j.updatedAt = :now, " +
           "j.workerId = null, j.leaseUntil = null, j.startedAt = null " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId")
    int markRetrying(@Param("id") UUID id, 
                     @Param("workerId") String workerId, 
                     @Param("retryCount") int retryCount, 
                     @Param("scheduledAt") LocalDateTime scheduledAt, 
                     @Param("errorMessage") String errorMessage, 
                     @Param("failedAt") LocalDateTime failedAt, 
                     @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Job j SET j.status = 'DEAD_LETTER', j.retryCount = :retryCount, " +
           "j.lastErrorMessage = :errorMessage, j.lastFailedAt = :failedAt, j.updatedAt = :now, " +
           "j.workerId = null, j.leaseUntil = null, j.startedAt = null " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId")
    int markDeadLetter(@Param("id") UUID id, 
                       @Param("workerId") String workerId, 
                       @Param("retryCount") int retryCount, 
                       @Param("errorMessage") String errorMessage, 
                       @Param("failedAt") LocalDateTime failedAt, 
                       @Param("now") LocalDateTime now);
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Job j SET j.status = 'CANCELLED', j.updatedAt = :now, " +
           "j.workerId = null, j.leaseUntil = null, j.startedAt = null, j.scheduledAt = null " +
           "WHERE j.id = :id AND (j.status = 'QUEUED' OR j.status = 'RETRYING')")
    int cancelQueuedOrRetrying(@Param("id") UUID id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Job j SET j.status = 'CANCELLED', j.updatedAt = :now, " +
           "j.workerId = null, j.leaseUntil = null, j.startedAt = null " +
           "WHERE j.id = :id AND j.status = 'RUNNING' AND j.workerId = :workerId")
    int cancelRunning(@Param("id") UUID id, 
                      @Param("workerId") String workerId, 
                      @Param("now") LocalDateTime now);
}