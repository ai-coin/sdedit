// Copyright (c) 2006 - 2011, Markus Strauch.
// All rights reserved.
// 
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// 
// * Redistributions of source code must retain the above copyright notice, 
// this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above copyright notice, 
// this list of conditions and the following disclaimer in the documentation 
// and/or other materials provided with the distribution.
// 
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
// THE POSSIBILITY OF SUCH DAMAGE.

package net.sf.sdedit.util;

import java.util.LinkedList;
import java.util.List;

import javax.swing.DefaultBoundedRangeModel;

@SuppressWarnings("serial")
public class Job extends DefaultBoundedRangeModel implements JobListener, Runnable {

    private LinkedList<Job> jobQueue;

    private Job currentJob;
    
    private List<JobListener> jobListeners;
    
    private String description;
    
    private Exception exception;
    
    private Thread thread;
    
    private boolean interrupted;
    
    public Job () {
        jobListeners = new LinkedList<JobListener>();
        setMinimum(0);
        setMaximum(0);
        setValue(0);
        description = "";
    }
    
    public Job (String description) {
        this ();
        this.description = description;
    }
    
    public void start () {
    	start(false);
    }
    
    public void start(boolean synchronous) {
    	if (synchronous) {
    		run();
    	} else {
            thread = new Thread(this);
            thread.start();    		
    	}
    }
    
    public void interrupt () {
        if (thread != null) {
            thread.interrupt();
            interrupted = true;
        }
    }
    
    public boolean isInterrupted () {
        return interrupted;
    }
    
    public String getDescription () {
        return description;
    }
    
    public void setDescription (String description) {
        this.description = description;
    }
    
    public void addJobListener (JobListener listener) {
        jobListeners.add(listener);
    }
    
    public void removeJobListener (JobListener listener) {
        jobListeners.remove(listener);
    }
    
    public Exception getException () {
        return exception;
    }

    public void run() {
        for (JobListener listener : jobListeners) {
            listener.jobStarted(this);
        }
        if (jobQueue != null) {
            synchronized (jobQueue) {
                setMaximum(jobQueue.size() - 1);
                setValue(0);
                while (!jobQueue.isEmpty()) {
                    currentJob = jobQueue.removeFirst();
                    setValue (getValue() + 1);
                    currentJob.run();
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    if (currentJob.getException() != null) {
                        exception = currentJob.getException();
                        break;
                    }
                }
                currentJob = null;
            }
        } else {
            try {
                doJob ();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                exception = ex;
            }
        }
        for (JobListener listener : jobListeners) {
            listener.jobEnded(this);
        }
    }
    
    public boolean isComposite () {
        return jobQueue != null;
    }

    public void addJob(Job job) {
        if (jobQueue == null) {
            jobQueue = new LinkedList<Job>();
        }
        synchronized (jobQueue) {
            jobQueue.addLast(job);
        }
        job.addJobListener(this);
    }
    
    public synchronized Job getCurrentJob () {
        return currentJob;
    }
    
    protected void doJob () throws Exception {
        /* empty by default */
    }
    
    public final void jobEnded(Job job) {
        for (JobListener listener : jobListeners) {
            listener.jobEnded(job);
        }
    }

    public final void jobStarted(Job job) {
        for (JobListener listener : jobListeners) {
            listener.jobStarted(job);
        }
    }
}
