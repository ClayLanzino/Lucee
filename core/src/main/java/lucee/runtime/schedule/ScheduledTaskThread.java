/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 **/
package lucee.runtime.schedule;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import lucee.commons.date.DateTimeUtil;
import lucee.commons.date.JREDateTimeUtil;
import lucee.commons.io.SystemUtil;
import lucee.commons.io.log.Log;
import lucee.commons.io.log.LogUtil;
import lucee.commons.lang.ExceptionUtil;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigImpl;
import lucee.runtime.engine.CFMLEngineImpl;
import lucee.runtime.engine.ThreadLocalConfig;
import lucee.runtime.engine.ThreadLocalPageContext;

public class ScheduledTaskThread extends Thread {

	private static final long DAY = 24 * 3600000;
	// private Calendar calendar;

	private long start;
	private long startDate;
	private long startTime;
	private long endDate;
	private long endTime;
	private int intervall;
	private int amount;
	private boolean stop;

	private DateTimeUtil util;

	// private int cIntervall;

	private ScheduleTask task;
	private final CFMLEngineImpl engine;
	private TimeZone timeZone;
	private SchedulerImpl scheduler;
	private List<ExecutionThread> exeThreads = new ArrayList<ExecutionThread>();
	private ExecutionThread exeThread;
	private final boolean unique;
	private Config config;

	public ScheduledTaskThread(CFMLEngineImpl engine, Scheduler scheduler, ScheduleTask task) {
		util = DateTimeUtil.getInstance();
		this.engine = engine;
		this.scheduler = (SchedulerImpl) scheduler;
		this.task = task;
		timeZone = ThreadLocalPageContext.getTimeZone(this.scheduler.getConfig());
		this.start = task.getStartDate().getTime();
		this.startDate = util.getMilliSecondsAdMidnight(timeZone, start);
		this.startTime = util.getMilliSecondsInDay(timeZone, start);
		this.endDate = task.getEndDate() == null ? Long.MAX_VALUE : util.getMilliSecondsAdMidnight(timeZone, task.getEndDate().getTime());
		this.endTime = task.getEndTime() == null ? DAY : util.getMilliSecondsInDay(timeZone, task.getEndTime().getTime());
		this.unique = ((ScheduleTaskImpl) task).unique();
		this.intervall = task.getInterval();
		if (intervall >= 10) {
			amount = intervall;
			intervall = ScheduleTaskImpl.INTERVAL_EVEREY;
		}
		else amount = 1;

		// cIntervall = toCalndarIntervall(intervall);
		this.config = ThreadLocalPageContext.getConfig(this.scheduler.getConfig());
	}

	public void setStop(boolean stop) {
		this.stop = stop;
	}

	public void stopIt() {
		setStop(true);
		Log log = scheduler.getConfig().getLog("scheduler");
		log.info("scheduler", "stopping task thread [" + task.getTask() + "]");

		if (unique) {
			stop(log, exeThread);
		}
		else {
			Iterator<ExecutionThread> it = exeThreads.iterator();
			while (it.hasNext()) {
				stop(log, it.next());
			}
			cleanThreads();
		}

		// stop this thread itself
		SystemUtil.notify(this);
		SystemUtil.stop(this);
		if (this.isAlive()) log.log(Log.LEVEL_WARN, "scheduler", "task [" + task.getTask() + "] could not be stopped.", ExceptionUtil.toThrowable(this.getStackTrace()));
		else log.info("scheduler", "task [" + task.getTask() + "] stopped");
	}

	private void stop(Log log, ExecutionThread et) {
		SystemUtil.stop(exeThread);
		if (et != null && et.isAlive())
			log.log(Log.LEVEL_WARN, "scheduler", "task thread [" + task.getTask() + "] could not be stopped.", ExceptionUtil.toThrowable(et.getStackTrace()));
		else log.info("scheduler", "task thread [" + task.getTask() + "] stopped");
	}

	@Override
	public void run() {
		if (ThreadLocalPageContext.getConfig() == null && config != null) ThreadLocalConfig.register(config);

		try {
			_run();
		}
		catch (Exception e) {
			log(Log.LEVEL_ERROR, e);
			if (e instanceof RuntimeException) throw (RuntimeException) e;
			throw new RuntimeException(e);
		}
		finally {
			log(Log.LEVEL_INFO, "ending task");
			task.setValid(false);
			try {
				scheduler.removeIfNoLonerValid(task);
			}
			catch (Exception e) {}
		}

	}

	public void _run() {

		// check values
		if (startDate > endDate) {
			log(Log.LEVEL_ERROR, "Invalid task definition: enddate is before startdate");
			return;
		}
		if (intervall == ScheduleTaskImpl.INTERVAL_EVEREY && startTime > endTime) {
			log(Log.LEVEL_ERROR, "Invalid task definition: endtime is before starttime");
			return;
		}

		long today = System.currentTimeMillis();
		long execution;
		boolean isOnce = intervall == ScheduleTask.INTERVAL_ONCE;
		if (isOnce) {
			if (startDate + startTime < today) {
				log(Log.LEVEL_INFO, "not executing task because single execution was in the past");
				return;
			}
			execution = startDate + startTime;
		}
		else execution = calculateNextExecution(today, false);
		// long sleep=execution-today;

		log(Log.LEVEL_INFO, "First execution");

		while (true) {
			sleepEL(execution, today);
			if (stop) break;
			if (!engine.isRunning()) {
				log(Log.LEVEL_ERROR, "Engine is not running");
				break;
			}

			today = System.currentTimeMillis();
			long todayTime = util.getMilliSecondsInDay(null, today);
			long todayDate = today - todayTime;

			if (!task.isValid()) {
				log(Log.LEVEL_ERROR, "Task is not valid");
				break;
			}
			if (!task.isPaused()) {
				if (endDate < todayDate && endTime < todayTime) {
					log(Log.LEVEL_ERROR, String.format("End date %s has passed; now: %s", DateTimeUtil.format(endDate + endTime, null, timeZone),
							DateTimeUtil.format(todayDate + todayTime, null, timeZone)));
					break;
				}
				execute();
			}
			if (isOnce) {
				log(Log.LEVEL_INFO, "ending task after a single execution");
				break;
			}
			today = System.currentTimeMillis();
			execution = calculateNextExecution(today, true);

			if (!task.isPaused()) log(Log.LEVEL_DEBUG, "next execution runs at " + DateTimeUtil.format(execution, null, timeZone));
			// sleep=execution-today;
		}
	}

	private void log(int level, String msg) {
		try {
			String logName = "schedule task:" + task.getTask();
			((ConfigImpl) scheduler.getConfig()).getLog("scheduler").log(level, logName, msg);

		}
		catch (Exception e) {
			System.err.println(msg);
			System.err.println(e);
		}
	}

	private void log(int level, Exception e) {
		try {
			String logName = "schedule task:" + task.getTask();
			((ConfigImpl) scheduler.getConfig()).getLog("scheduler").log(level, logName, e);

		}
		catch (Exception ee) {
			LogUtil.logGlobal(config, "scheduler", e);
			LogUtil.logGlobal(config, "scheduler", ee);
		}
	}

	private void sleepEL(long when, long now) {
		long millis = when - now;

		try {
			while (true) {
				SystemUtil.wait(this, millis);
				millis = when - System.currentTimeMillis();
				if (millis <= 0) break;
				millis = 10;
			}
		}
		catch (Exception e) {
			log(Log.LEVEL_ERROR, e);
		}

	}

	private void execute() {
		if (scheduler.getConfig() != null) {
			// unique
			if (unique && exeThread != null && exeThread.isAlive()) {
				return;
			}

			ExecutionThread et = new ExecutionThread(scheduler.getConfig(), task, scheduler.getCharset());
			et.start();
			if (unique) {
				exeThread = et;
			}
			else {
				cleanThreads();
				exeThreads.add(et);
			}
		}
	}

	private void cleanThreads() {
		List<ExecutionThread> list = new ArrayList<ExecutionThread>();
		Iterator<ExecutionThread> it = exeThreads.iterator();
		ExecutionThread et;
		while (it.hasNext()) {
			et = it.next();
			if (et.isAlive()) list.add(et);
		}
		exeThreads = list;
	}

	/*
	 * public static void main(String[] args) { test(29, 2); test(28, 3); test(29, 3); test(30, 3);
	 * test(31, 3);
	 * 
	 * test(24, 10); test(25, 10); test(26, 10); }
	 * 
	 * public static void test(int day, int month) { int inter = ScheduleTaskImpl.INTERVAL_DAY; TimeZone
	 * tz = TimeZone.getTimeZone("CET");
	 * 
	 * Calendar s = JREDateTimeUtil.getThreadCalendar(tz); s.set(Calendar.YEAR, 2020);
	 * s.set(Calendar.MONTH, 0); s.set(Calendar.DAY_OF_MONTH, 1); s.set(Calendar.HOUR, 2);
	 * s.set(Calendar.MINUTE, 30); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0); long start
	 * = s.getTimeInMillis();
	 * 
	 * Calendar c = JREDateTimeUtil.getThreadCalendar(tz); c.set(Calendar.YEAR, 2020);
	 * c.set(Calendar.MONTH, month - 1); c.set(Calendar.DAY_OF_MONTH, day); c.set(Calendar.HOUR, 2);
	 * c.set(Calendar.MINUTE, 30); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
	 * 
	 * print.e("-----------------------"); // print.e("srt:" + new Date(start)); print.e("now:" +
	 * c.getTime());
	 * 
	 * DateTimeUtil util = DateTimeUtil.getInstance();
	 * 
	 * long endTime = 23 * 60 * 60 * 100;
	 * 
	 * long res = calculateNextExecution(util, c.getTimeInMillis(), true, tz, start, endTime, inter,
	 * 65);
	 * 
	 * print.e("res:" + new Date(res));
	 * 
	 * }
	 */

	private long calculateNextExecution(long now, boolean notNow) {
		return calculateNextExecution(util, now, notNow, timeZone, start, endTime, intervall, amount);
	}

	private static long calculateNextExecution(DateTimeUtil util, long now, boolean notNow, TimeZone timeZone, long start, long endTime, int intervall, int amount) {

		// set the start datetime
		Calendar c = JREDateTimeUtil.getThreadCalendar(timeZone);
		c.setTimeInMillis(start);

		// extract the time in day info (we do not seconds in day to avoid DST issues)
		int startHour = c.get(Calendar.HOUR_OF_DAY);
		int startMinute = c.get(Calendar.MINUTE);
		int startSecond = c.get(Calendar.SECOND);
		int startMilliSecond = c.get(Calendar.MILLISECOND);

		// set time in today to today
		c.setTimeInMillis(now);
		c.set(Calendar.HOUR_OF_DAY, startHour);
		c.set(Calendar.MINUTE, startMinute);
		c.set(Calendar.SECOND, startSecond);
		c.set(Calendar.MILLISECOND, startMilliSecond);

		long next = c.getTimeInMillis();

		// is it already in the future or we want not now
		if (next < now || (notNow && (next - now) < 1000)) {
			if (intervall == ScheduleTaskImpl.INTERVAL_DAY) c.add(Calendar.DAY_OF_MONTH, 1);
			else if (intervall == ScheduleTaskImpl.INTERVAL_WEEK) c.add(Calendar.WEEK_OF_YEAR, 1);
			else if (intervall == ScheduleTaskImpl.INTERVAL_MONTH) c.add(Calendar.MONTH, 1);
			else if (intervall == ScheduleTaskImpl.INTERVAL_YEAR) c.add(Calendar.YEAR, 1);
			else if (intervall == ScheduleTaskImpl.INTERVAL_EVEREY) {
				c.add(Calendar.SECOND, amount);
				return c.getTimeInMillis();
			}

			c.set(Calendar.HOUR_OF_DAY, startHour);
			c.set(Calendar.MINUTE, startMinute);
			c.set(Calendar.SECOND, startSecond);
			c.set(Calendar.MILLISECOND, startMilliSecond);

			return c.getTimeInMillis();
		}
		return next;
	}

	public static long getMilliSecondsInDay(Calendar c) {
		return (c.get(Calendar.HOUR_OF_DAY) * 3600000) + (c.get(Calendar.MINUTE) * 60000) + (c.get(Calendar.SECOND) * 1000) + (c.get(Calendar.MILLISECOND));
	}

	public Config getConfig() {
		return scheduler.getConfig();
	}

	public ScheduleTask getTask() {
		return task;
	}
}
