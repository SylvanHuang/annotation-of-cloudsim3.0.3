/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 随机资源使用模型： 返回一个随机的资源使用率
 * The UtilizationModelStochastic class implements a model, according to which a Cloudlet generates
 * random CPU utilization every time frame.
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
public class UtilizationModelStochastic implements UtilizationModel {

	/** The random generator. */
	// 随机数生成器
	private Random randomGenerator;

	/** The history. */
	// 存放历史资源使用率的表： 
	//     键-时间 
	//     值-资源使用率
	private Map<Double, Double> history;

	/**
	 * Instantiates a new utilization model stochastic.
	 */
	public UtilizationModelStochastic() {
		setHistory(new HashMap<Double, Double>());
		setRandomGenerator(new Random());
	}

	/**
	 * Instantiates a new utilization model stochastic.
	 * 
	 * @param seed the seed
	 */
	public UtilizationModelStochastic(long seed) {
		setHistory(new HashMap<Double, Double>());
		setRandomGenerator(new Random(seed));
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.power.UtilizationModel#getUtilization(double)
	 */
	@Override
	public double getUtilization(double time) {
		// ？？？？？？？？？？
		if (getHistory().containsKey(time)) {
			return getHistory().get(time);
		}
		//返回下一个伪随机数，它是取自此随机数生成器序列的、在 0.0 和 1.0 之间均匀分布的 double 值。 
		//nextDouble 的常规协定是，伪随机地生成并返回一个从 0.0d（包括）到 1.0d（不包括）范围内
		//均匀选择（大致）的 double 值。 
		double utilization = getRandomGenerator().nextDouble();
		// 将生成的资源使用率加入到历史记录中
		getHistory().put(time, utilization);
		return utilization;
	}

	/**
	 * Gets the history.
	 * 
	 * @return the history
	 */
	protected Map<Double, Double> getHistory() {
		return history;
	}

	/**
	 * Sets the history.
	 * 
	 * @param history the history
	 */
	protected void setHistory(Map<Double, Double> history) {
		this.history = history;
	}

	/**
	 * Save history.
	 * 
	 * @param filename the filename
	 * @throws Exception the exception
	 */
	public void saveHistory(String filename) throws Exception {
		FileOutputStream fos = new FileOutputStream(filename);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(getHistory());
		oos.close();
	}

	/**
	 * Load history.
	 * 
	 * @param filename the filename
	 * @throws Exception the exception
	 */
	@SuppressWarnings("unchecked")
	public void loadHistory(String filename) throws Exception {
		FileInputStream fis = new FileInputStream(filename);
		ObjectInputStream ois = new ObjectInputStream(fis);
		setHistory((Map<Double, Double>) ois.readObject());
		ois.close();
	}

	/**
	 * Sets the random generator.
	 * 
	 * @param randomGenerator the new random generator
	 */
	public void setRandomGenerator(Random randomGenerator) {
		this.randomGenerator = randomGenerator;
	}

	/**
	 * Gets the random generator.
	 * 
	 * @return the random generator
	 */
	public Random getRandomGenerator() {
		return randomGenerator;
	}

}
