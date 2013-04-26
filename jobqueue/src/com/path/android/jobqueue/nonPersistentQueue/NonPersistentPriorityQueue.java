package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class NonPersistentPriorityQueue implements JobQueue {
    private long nonPersistentJobIdGenerator = Integer.MIN_VALUE;
    private PriorityQueue<JobHolder> jobs;
    private Map<Long, JobHolder> runningJobs;
    private final String id;
    private final long sessionId;

    public NonPersistentPriorityQueue(long sessionId, String id) {
        this.id = id;
        this.sessionId = sessionId;
        jobs = new PriorityQueue<JobHolder>(5, jobComparator);
        runningJobs = new HashMap<Long, JobHolder>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized long insert(JobHolder jobHolder) {
        nonPersistentJobIdGenerator++;
        jobHolder.setId(nonPersistentJobIdGenerator);
        jobs.add(jobHolder);
        return jobHolder.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long insertOrReplace(JobHolder jobHolder) {
        remove(jobHolder);
        jobHolder.setRunningSessionId(JobManager.NOT_RUNNING_SESSION_ID);
        jobs.add(jobHolder);
        return jobHolder.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(JobHolder jobHolder) {
        jobs.remove(jobHolder);
        if (jobHolder.getId() != null) {
            runningJobs.remove(jobHolder.getId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int count() {
        return jobs.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder nextJobAndIncRunCount() {
        JobHolder jobHolder = jobs.poll();
        if (jobHolder != null) {
            //check if job can run
            if(jobHolder.getDelayUntilNs() > System.nanoTime()) {
                jobs.add(jobHolder);
                jobHolder = null;
            } else {
                jobHolder.setRunningSessionId(sessionId);
                jobHolder.setRunCount(jobHolder.getRunCount() + 1);
                //add it back to the queue. it will go the end
                runningJobs.put(jobHolder.getId(), jobHolder);
            }
        }
        return jobHolder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNextJobDelayUntilNs() {
        JobHolder jobHolder = jobs.peek();
        return jobHolder == null ? null : jobHolder.getDelayUntilNs();
    }

    public final Comparator<JobHolder> jobComparator = new Comparator<JobHolder>() {
        @Override
        public int compare(JobHolder holder1, JobHolder holder2) {
            //job can run earlier first
            int cmp = -compareLong(holder1.getDelayUntilNs(), holder2.getDelayUntilNs());
            if(cmp != 0) {
                return cmp;
            }

            //high priority first
            cmp = compareInt(holder1.getPriority(), holder2.getPriority());
            if(cmp != 0) {
                return cmp;
            }

            //if priorities are equal, less running job first
            cmp = -compareInt(holder1.getRunCount(), holder2.getRunCount());
            if(cmp != 0) {
                return cmp;
            }

            //if run counts are also equal, older job first
            cmp = -compareLong(holder1.getCreatedNs(), holder2.getCreatedNs());
            if(cmp != 0) {
                return cmp;
            }

            //if jobs were created at the same time, smaller id first
            return -compareLong(holder1.getId(), holder2.getId());
        }
    };

    private static int compareInt(int i1, int i2) {
        if (i1 > i2) {
            return -1;
        }
        if (i2 > i1) {
            return 1;
        }
        return 0;
    }

    private static int compareLong(long l1, long l2) {
        if (l1 > l2) {
            return -1;
        }
        if (l2 > l1) {
            return 1;
        }
        return 0;
    }


}
