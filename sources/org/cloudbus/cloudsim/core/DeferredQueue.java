/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.core;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * 延迟事件队列，所有的事件必须经过从future队列中移除之后，进入延迟事件队列，才能被各个实体进行处理来完成相应的任务
 * This class implements the deferred event queue used by {@link Simulation}. The event queue uses a
 * linked list to store the events.
 * 
 * @author Marcos Dias de Assuncao
 * @since CloudSim Toolkit 1.0
 * @see Simulation
 * @see SimEvent
 */
public class DeferredQueue {

	/** 注意延迟队列是一个列表 The list. */
	private final List<SimEvent> list = new LinkedList<SimEvent>();

	/** 列表中的事件，最大的执行时钟 The max time. */
	private double maxTime = -1;

	/**
	 * Adds a new event to the queue. Adding a new event to the queue preserves the temporal order
	 * of the events.
	 * 将事件加入延迟队列，但是会保证他在时间上有序的
	 * 
	 * @param newEvent The event to be added to the queue.
	 */
	public void addEvent(SimEvent newEvent) {
		// The event has to be inserted as the last of all events
		// with the same event_time(). Yes, this matters.
		double eventTime = newEvent.eventTime();
		if (eventTime >= maxTime) {
			list.add(newEvent);
			maxTime = eventTime;
			return;
		}

		ListIterator<SimEvent> iterator = list.listIterator();
		SimEvent event;
		while (iterator.hasNext()) {
			event = iterator.next();
			if (event.eventTime() > eventTime) {
				iterator.previous();
				iterator.add(newEvent);	//保证在事件在执行时间上面是有序的
				return;
			}
		}

		list.add(newEvent);
	}

	/**
	 * Returns an iterator to the events in the queue.
	 * 
	 * @return the iterator
	 */
	public Iterator<SimEvent> iterator() {
		return list.iterator();
	}

	/**
	 * Returns the size of this event queue.
	 * 
	 * @return the number of events in the queue.
	 */
	public int size() {
		return list.size();
	}

	/**
	 * Clears the queue.
	 */
	public void clear() {
		list.clear();
	}

}