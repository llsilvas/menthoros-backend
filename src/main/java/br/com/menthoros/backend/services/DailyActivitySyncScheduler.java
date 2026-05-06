package br.com.menthoros.backend.services;

/**
 * Scheduler interface for daily activity synchronization.
 * Handles incremental sync of Strava activities and automatic matching with planned workouts.
 */
public interface DailyActivitySyncScheduler {
    void executeDailySync();
}
