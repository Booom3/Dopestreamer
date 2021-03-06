package com.dopelives.dopestreamer.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import com.dopelives.dopestreamer.util.Executor;

/**
 * A console to execute commands in.
 */
public class Console {

    /** The number counting the amount of consoles opened */
    private static int sConsoleCount = 0;

    /** The object to sync threads on */
    private final Object mSync = this;

    /** The number indicating this console's count */
    private final int mConsoleCount;
    /** The listeners to receive updates of this console */
    private final Collection<ConsoleListener> mListeners = new HashSet<>();

    /** The prefix to use for printing */
    final private String mPrefix;

    /** The builder that can start the process */
    private final ProcessBuilder mBuilder;
    /** The process in which the command is executed */
    private Process mProcess;
    /** The PID of the console process */
    private ProcessId mProcessId;

    /** Whether or not the console is currently starting the process */
    private boolean mStarting = false;
    /** Whether or not the console is currently running */
    private boolean mRunning = false;
    /** Whether or not the console should be stopping */
    private boolean mStopping = false;
    /** Whether or not the console has ran and stopped */
    private boolean mStopped = false;

    /**
     * Starts a new console while executing the specified command within a shell.
     *
     * @param command
     *            The command to execute
     */
    /* default */Console(final ProcessBuilder builder) {
        // Keep track of which console we're using
        mConsoleCount = ++sConsoleCount;
        mPrefix = "[" + mConsoleCount + "] ";

        mBuilder = builder;
        mBuilder.redirectErrorStream(true);
    }

    /**
     * Executes the given command. May not be called while the process is already active.
     */
    public synchronized void start() {
        synchronized (mSync) {
            if (mStarting || mRunning || mStopped) {
                throw new IllegalStateException("Console already executed");
            }
            mStarting = true;

            System.out.println(mPrefix + "START");

            // Execute the command
            Executor.execute(new ProcessRunner());

            // Wait for the process to be started (doesn't require command to be finish)
            if (mStarting) {
                try {
                    wait();
                } catch (final InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Forcefully stops the running process.
     */
    public synchronized void stop() {
        synchronized (mSync) {
            mStopping = true;
            if (mProcessId != null) {
                Shell.getInstance().killProcessTree(mProcessId);
            }
        }
    }

    /**
     * The runnable containing the process to run in parallel.
     */
    private class ProcessRunner implements Runnable {

        /** The stream from which to get the results */
        private BufferedReader mOutput;

        @Override
        public void run() {
            try {
                synchronized (mSync) {
                    if (mStopping) {
                        // Don't start the process, but exit nicely through the finally block
                        return;
                    }

                    // Start process and retrieve streams
                    mProcess = mBuilder.start();
                    mProcessId = Shell.getInstance().getProcessId(mProcess);
                    mOutput = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));

                    // Keep track of the output
                    mRunning = true;
                    Executor.execute(() -> {
                        while (mRunning) {
                            try {
                                final String line = mOutput.readLine();
                                if (line != null) {
                                    System.out.println(mPrefix + line);
                                    for (final ConsoleListener listener : mListeners) {
                                        listener.onConsoleOutput(mProcessId, line);
                                    }
                                }
                            } catch (final IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    });

                    // Notify that the process has started
                    mStarting = false;
                    mSync.notifyAll();
                }

                // Wait for the process to finish
                try {
                    mProcess.waitFor();
                } catch (final InterruptedException ex) {
                    ex.printStackTrace();
                }

            } catch (final IOException ex) {
                ex.printStackTrace();

            } finally {
                // Shut down the console
                mRunning = false;
                mStopped = true;
                try {
                    if (mOutput != null) {
                        mOutput.close();
                    }
                } catch (final IOException ex) {
                    ex.printStackTrace();
                }

                // Inform the listeners that the process has stopped
                mListeners.forEach(l -> l.onConsoleStop(mProcessId));

                System.out.println(mPrefix + "STOP");
            }
        }
    }

    /**
     * Adds a listener to this console that will receive call-backs on console events such as output and stop.
     *
     * @param listener
     *            The listener that will receive the call-backs
     */
    public void addListener(final ConsoleListener listener) {
        mListeners.add(listener);
    }

    /**
     * @return The listeners that will receive the call-backs
     */
    public Collection<ConsoleListener> getListeners() {
        return Collections.unmodifiableCollection(mListeners);
    }

    /**
     * @return The process ID of this console or the negative console count if there isn't any yet
     */
    public ProcessId getProcessId() {
        return (mProcessId != null ? mProcessId : new ProcessId(-mConsoleCount));
    }

    /**
     * @return True iff the console process is running
     */
    public boolean isRunning() {
        return mRunning;
    }
}
