/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.HostDynamicWorkload;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.power.Constants;
import org.cloudbus.cloudsim.power.lists.PowerVmList;
import org.cloudbus.cloudsim.util.ExecutionTimeMeasurer;

/**
 * The class of an abstract power-aware VM allocation policy that dynamically optimizes the VM
 * allocation using migration.
 * 
 * If you are using any algorithms, policies or workload included in the power package, please cite
 * the following paper:
 * 
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 3.0
 */
public abstract class PowerVmAllocationPolicyMigrationAbstract extends PowerVmAllocationPolicyAbstract {

	/** 虚拟机选择策略 The vm selection policy. */
	private PowerVmSelectionPolicy vmSelectionPolicy;
	
	/** The policy of vm placement in host, for example: BestPower, FirstResource, BestResource and so on. **/
	private String findHostPolicy = "";

	/** The saved allocation. */
	private final List<Map<String, Object>> savedAllocation = new ArrayList<Map<String, Object>>();

	/** The utilization history. */
	private final Map<Integer, List<Double>> utilizationHistory = new HashMap<Integer, List<Double>>();

	/** The metric history. */
	private final Map<Integer, List<Double>> metricHistory = new HashMap<Integer, List<Double>>();

	/** The time history. */
	private final Map<Integer, List<Double>> timeHistory = new HashMap<Integer, List<Double>>();

	/** 虚拟机放置优化算法中 从过载的Host中选择出迁移的虚拟机所消耗的时间历史记录 The execution time history vm selection. */
	private final List<Double> executionTimeHistoryVmSelection = new LinkedList<Double>();

	/** 虚拟机放置优化算法中 过载Host判断消耗的历史时间 The execution time history host selection. */
	private final List<Double> executionTimeHistoryHostSelection = new LinkedList<Double>();

	/** 虚拟机放置优化算法中 需要迁移的虚拟机重新分配消耗的时间 The execution time history vm reallocation. */
	private final List<Double> executionTimeHistoryVmReallocation = new LinkedList<Double>();

	/** The execution time history total. */
	private final List<Double> executionTimeHistoryTotal = new LinkedList<Double>();

	/**
	 * Instantiates a new power vm allocation policy migration abstract.
	 * 
	 * @param hostList the host list
	 * @param vmSelectionPolicy the vm selection policy
	 */
	public PowerVmAllocationPolicyMigrationAbstract(
			List<? extends Host> hostList,
			PowerVmSelectionPolicy vmSelectionPolicy) {
		super(hostList);
		setVmSelectionPolicy(vmSelectionPolicy);
		setFindHostPolicy(Constants.findHostPolicy);
	}

	
	public String getFindHostPolicy() {
		return findHostPolicy;
	}
	
	public void setFindHostPolicy(String findHostPolicy) {
		this.findHostPolicy = findHostPolicy;
	}
	/**
	 * 在PowerDatacenter.updateCloudletProcessing()函数中被调用
	 * Algorithm 1：VM Placement Optimization
	 * Optimize allocation of the VMs according to current utilization.
	 * 
	 * @param vmList the vm list
	 * 
	 * @return the array list< hash map< string, object>>
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
		ExecutionTimeMeasurer.start("optimizeAllocationTotal");//记录总的VPO算法执行的时间

		ExecutionTimeMeasurer.start("optimizeAllocationHostSelection");//记录过载主机选择时间
		//1， 遍历主机列表，取出，有过载的主机
		List<PowerHostUtilizationHistory> overUtilizedHosts = getOverUtilizedHosts();
		getExecutionTimeHistoryHostSelection().add(
				ExecutionTimeMeasurer.end("optimizeAllocationHostSelection"));
		//输出过载Host的信息
		printOverUtilizedHosts(overUtilizedHosts);
		//???保存当前分配状态
		saveAllocation();
		//2,从过载的Host中选择出迁移的虚拟机
		ExecutionTimeMeasurer.start("optimizeAllocationVmSelection");
		List<? extends Vm> vmsToMigrate = getVmsToMigrateFromHosts(overUtilizedHosts);
		getExecutionTimeHistoryVmSelection().add(ExecutionTimeMeasurer.end("optimizeAllocationVmSelection"));

		Log.printLine("Reallocation of VMs from the over-utilized hosts:");
		
		//3,将需要迁移的虚拟机从新分配,并返回迁移的虚拟机和Host的映射表
		ExecutionTimeMeasurer.start("optimizeAllocationVmReallocation");
		List<Map<String, Object>> migrationMap = getNewVmPlacement(vmsToMigrate, new HashSet<Host>(
				overUtilizedHosts));
		getExecutionTimeHistoryVmReallocation().add(
				ExecutionTimeMeasurer.end("optimizeAllocationVmReallocation"));
		Log.printLine();

		migrationMap.addAll(
				//4，将欠载的Host中虚拟机找出来，并重新分配
				getMigrationMapFromUnderUtilizedHosts(overUtilizedHosts));

		restoreAllocation();

		getExecutionTimeHistoryTotal().add(ExecutionTimeMeasurer.end("optimizeAllocationTotal"));

		return migrationMap;
	}

	/**
	 * 
	 * Gets the migration map from under utilized hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 * @return the migration map from under utilized hosts
	 */
	protected List<Map<String, Object>> getMigrationMapFromUnderUtilizedHosts(
			List<PowerHostUtilizationHistory> overUtilizedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		List<PowerHost> switchedOffHosts = getSwitchedOffHosts();//关闭的Host

		// over-utilized hosts + hosts that are selected to migrate VMs to from over-utilized hosts
		// 排除在欠载列表之外的Host列表，显然上一步找到的过载Host肯定不在潜在列表中，还有关闭的Host也在排除列表之内
		Set<PowerHost> excludedHostsForFindingUnderUtilizedHost = new HashSet<PowerHost>();
		excludedHostsForFindingUnderUtilizedHost.addAll(overUtilizedHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(switchedOffHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(migrationMap));

		// over-utilized + under-utilized hosts
		// 排除在虚拟机迁移列表之外的Host，显然过载的Host肯定不在虚拟机可以迁移的Host之内，关闭的Host也在排除列表之内
		Set<PowerHost> excludedHostsForFindingNewVmPlacement = new HashSet<PowerHost>();
		excludedHostsForFindingNewVmPlacement.addAll(overUtilizedHosts);
		excludedHostsForFindingNewVmPlacement.addAll(switchedOffHosts);

		int numberOfHosts = getHostList().size();

		while (true) {
			// 如果Host都是在排除列表之外，那么退出循环
			if (numberOfHosts == excludedHostsForFindingUnderUtilizedHost.size()) {
				break;
			}
			
			PowerHost underUtilizedHost = getUnderUtilizedHost(excludedHostsForFindingUnderUtilizedHost);
			if (underUtilizedHost == null) {
				break;
			}

			Log.printLine("Under-utilized host: host #" + underUtilizedHost.getId() + "\n");
			//把找到的欠载host加入到欠载排除列表和虚拟机放置排除列表中
			excludedHostsForFindingUnderUtilizedHost.add(underUtilizedHost);
			excludedHostsForFindingNewVmPlacement.add(underUtilizedHost);
			
			//将欠载Host中的可以迁移的虚拟机列表返回
			List<? extends Vm> vmsToMigrateFromUnderUtilizedHost = getVmsToMigrateFromUnderUtilizedHost(underUtilizedHost);
			if (vmsToMigrateFromUnderUtilizedHost.isEmpty()) {
				continue;
			}

			Log.print("Reallocation of VMs from the under-utilized host: ");
			if (!Log.isDisabled()) {
				for (Vm vm : vmsToMigrateFromUnderUtilizedHost) {
					Log.print(vm.getId() + " ");
				}
			}
			Log.printLine();
			// 在欠载Host中，对可以迁移的虚拟机进行迁移
			List<Map<String, Object>> newVmPlacement = getNewVmPlacementFromUnderUtilizedHost(
					vmsToMigrateFromUnderUtilizedHost,
					excludedHostsForFindingNewVmPlacement);

			excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(newVmPlacement));

			migrationMap.addAll(newVmPlacement);
			Log.printLine();
		}
		//返回迁移的映射表
		return migrationMap;
	}

	/**
	 * Prints the over utilized hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 */
	protected void printOverUtilizedHosts(List<PowerHostUtilizationHistory> overUtilizedHosts) {
		if (!Log.isDisabled()) {
			Log.printLine("Over-utilized hosts:");
			for (PowerHostUtilizationHistory host : overUtilizedHosts) {
				Log.printLine("Host #" + host.getId());
			}
			Log.printLine();
		}
	}

	/**
	 * Find host for vm.
	 * 
	 * @param vm the vm
	 * @param excludedHosts the excluded hosts
	 * @return the power host
	 */
	public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) {
 		if ("BestPower".equals(getFindHostPolicy())) {
 			return findHostForVmBestPower(vm, excludedHosts);
 		} else {
 			System.out.println("error: There is no " + getFindHostPolicy() + " policy.");
 			System.exit(0);
 		}
		return null;
	}
	/**
	 * Find host for vm best power.
	 * 
	 * @param vm the vm
	 * @param excludedHosts the excluded hosts
	 * @return the power host
	 */
	public PowerHost findHostForVmBestPower(Vm vm, Set<? extends Host> excludedHosts) {
 		double minPower = Double.MAX_VALUE;
		PowerHost allocatedHost = null;

		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			if (host.isSuitableForVm(vm)) {
				if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
					continue;
				}

				try {
					double powerAfterAllocation = getPowerAfterAllocation(host, vm);	//分配之后的能耗
					if (powerAfterAllocation != -1) {
						double powerDiff = powerAfterAllocation - host.getPower();
						if (powerDiff < minPower) {//选择分配能耗最小的
							minPower = powerDiff;
							allocatedHost = host;
						}
					}
				} catch (Exception e) {
				}
			}
		}
		return allocatedHost;
	}

	/**
	 * Checks if is host over utilized after allocation.
	 * 
	 * @param host the host
	 * @param vm the vm
	 * @return true, if is host over utilized after allocation
	 */
	protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
		boolean isHostOverUtilizedAfterAllocation = true;
		if (host.vmCreate(vm)) {
			isHostOverUtilizedAfterAllocation = isHostOverUtilized(host);
			host.vmDestroy(vm);
		}
		return isHostOverUtilizedAfterAllocation;
	}

	/**
	 * Find host for vm.
	 * 
	 * @param vm the vm
	 * @return the power host
	 */
	@Override
	public PowerHost findHostForVm(Vm vm) {
		Set<Host> excludedHosts = new HashSet<Host>();
		if (vm.getHost() != null) {
			excludedHosts.add(vm.getHost());
		}
		return findHostForVm(vm, excludedHosts);
	}

	/**
	 * Extract host list from migration map.
	 * 
	 * @param migrationMap the migration map
	 * @return the list
	 */
	protected List<PowerHost> extractHostListFromMigrationMap(List<Map<String, Object>> migrationMap) {
		List<PowerHost> hosts = new LinkedList<PowerHost>();
		for (Map<String, Object> map : migrationMap) {
			hosts.add((PowerHost) map.get("host"));
		}
		return hosts;
	}

	/**
	 * Gets the new vm placement.
	 * 
	 * @param vmsToMigrate the vms to migrate
	 * @param excludedHosts the excluded hosts
	 * @return the new vm placement
	 */
	protected List<Map<String, Object>> getNewVmPlacement(
			List<? extends Vm> vmsToMigrate,
			Set<? extends Host> excludedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		PowerVmList.sortByCpuUtilization(vmsToMigrate);
		for (Vm vm : vmsToMigrate) {
			PowerHost allocatedHost = findHostForVm(vm, excludedHosts);
			if (allocatedHost != null) {
				allocatedHost.vmCreate(vm);
				Log.printLine("VM #" + vm.getId() + " allocated to host #" + allocatedHost.getId());

				Map<String, Object> migrate = new HashMap<String, Object>();
				migrate.put("vm", vm);
				migrate.put("host", allocatedHost);
				migrationMap.add(migrate);
			}
		}
		return migrationMap;
	}

	/**
	 * Gets the new vm placement from under utilized host.
	 * 
	 * @param vmsToMigrate the vms to migrate
	 * @param excludedHosts the excluded hosts
	 * @return the new vm placement from under utilized host
	 */
	protected List<Map<String, Object>> getNewVmPlacementFromUnderUtilizedHost(
			List<? extends Vm> vmsToMigrate,
			Set<? extends Host> excludedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		PowerVmList.sortByCpuUtilization(vmsToMigrate);//按CPU使用率排序
		for (Vm vm : vmsToMigrate) {
			PowerHost allocatedHost = findHostForVm(vm, excludedHosts);
			if (allocatedHost != null) {
				allocatedHost.vmCreate(vm);
				Log.printLine("VM #" + vm.getId() + " allocated to host #" + allocatedHost.getId());

				Map<String, Object> migrate = new HashMap<String, Object>();
				migrate.put("vm", vm);
				migrate.put("host", allocatedHost);
				migrationMap.add(migrate);
			} else {//如果虚拟机迁移失败，则退出
				Log.printLine("Not all VMs can be reallocated from the host, reallocation cancelled");
				for (Map<String, Object> map : migrationMap) {
					((Host) map.get("host")).vmDestroy((Vm) map.get("vm"));
				}
				migrationMap.clear();
				break;
			}
		}
		return migrationMap;
	}

	/**
	 * 返回欠载Host中可以迁移的虚拟机列表
	 * Gets the vms to migrate from hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 * @return the vms to migrate from hosts
	 */
	protected
			List<? extends Vm>
			getVmsToMigrateFromHosts(List<PowerHostUtilizationHistory> overUtilizedHosts) {
		List<Vm> vmsToMigrate = new LinkedList<Vm>();
		for (PowerHostUtilizationHistory host : overUtilizedHosts) {
			while (true) {
				Vm vm = getVmSelectionPolicy().getVmToMigrate(host);
				if (vm == null) {
					break;
				}
				vmsToMigrate.add(vm);
				host.vmDestroy(vm);
				if (!isHostOverUtilized(host)) {
					break;
				}
			}
		}
		return vmsToMigrate;
	}

	/**
	 * Gets the vms to migrate from under utilized host.
	 * 
	 * @param host the host
	 * @return the vms to migrate from under utilized host
	 */
	protected List<? extends Vm> getVmsToMigrateFromUnderUtilizedHost(PowerHost host) {
		List<Vm> vmsToMigrate = new LinkedList<Vm>();
		for (Vm vm : host.getVmList()) {
			if (!vm.isInMigration()) {
				vmsToMigrate.add(vm);	
			}
		}
		return vmsToMigrate;
	}

	/**
	 * Gets the over utilized hosts.
	 * 
	 * @return the over utilized hosts
	 */
	protected List<PowerHostUtilizationHistory> getOverUtilizedHosts() {
		List<PowerHostUtilizationHistory> overUtilizedHosts = new LinkedList<PowerHostUtilizationHistory>();
		for (PowerHostUtilizationHistory host : this.<PowerHostUtilizationHistory> getHostList()) {
			if (isHostOverUtilized(host)) {
				overUtilizedHosts.add(host);
			}
		}
		return overUtilizedHosts;
	}

	/**
	 * Gets the switched off host.
	 * 
	 * @return the switched off host
	 */
	protected List<PowerHost> getSwitchedOffHosts() {
		List<PowerHost> switchedOffHosts = new LinkedList<PowerHost>();
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (host.getUtilizationOfCpu() == 0) {
				switchedOffHosts.add(host);
			}
		}
		return switchedOffHosts;
	}

	/**
	 * 返回一个欠载的Host，是找到一个最小负载的Host作为欠载Host返回
	 * Gets the under utilized host.
	 * 
	 * @param excludedHosts the excluded hosts
	 * @return the under utilized host
	 */
	protected PowerHost getUnderUtilizedHost(Set<? extends Host> excludedHosts) {
		double minUtilization = 1;
		PowerHost underUtilizedHost = null;
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;//在排除Host列表中，不予考虑
			}
			//找到最小的负载的Host，做为欠载Host返回
			double utilization = host.getUtilizationOfCpu();
			if (utilization > 0 && utilization < minUtilization
					&& !areAllVmsMigratingOutOrAnyVmMigratingIn(host)) {
				minUtilization = utilization;
				underUtilizedHost = host;
			}
		}
		return underUtilizedHost;
	}

	/**
	 * Checks whether all vms are in migration.
	 * 
	 * @param host the host
	 * @return true, if successful
	 */
	protected boolean areAllVmsMigratingOutOrAnyVmMigratingIn(PowerHost host) {
		for (PowerVm vm : host.<PowerVm> getVmList()) {
			if (!vm.isInMigration()) {
				return false;
			}
			if (host.getVmsMigratingIn().contains(vm)) {
				return true;
			}
		}
		return true;
	}

	/**
	 * Checks if is host over utilized.
	 * 
	 * @param host the host
	 * @return true, if is host over utilized
	 */
	protected abstract boolean isHostOverUtilized(PowerHost host);

	/**
	 * Adds the history value.
	 * 
	 * @param host the host
	 * @param metric the metric
	 */
	protected void addHistoryEntry(HostDynamicWorkload host, double metric) {
		int hostId = host.getId();
		if (!getTimeHistory().containsKey(hostId)) {
			getTimeHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getUtilizationHistory().containsKey(hostId)) {
			getUtilizationHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getMetricHistory().containsKey(hostId)) {
			getMetricHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getTimeHistory().get(hostId).contains(CloudSim.clock())) {
			getTimeHistory().get(hostId).add(CloudSim.clock());
			getUtilizationHistory().get(hostId).add(host.getUtilizationOfCpu());
			getMetricHistory().get(hostId).add(metric);
		}
	}

	/**
	 * Save allocation.
	 */
	protected void saveAllocation() {
		getSavedAllocation().clear();
		for (Host host : getHostList()) {
			for (Vm vm : host.getVmList()) {
				if (host.getVmsMigratingIn().contains(vm)) {
					continue;
				}
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("host", host);
				map.put("vm", vm);
				getSavedAllocation().add(map);
			}
		}
	}

	/**
	 * Restore allocation.
	 */
	protected void restoreAllocation() {
		for (Host host : getHostList()) {
			host.vmDestroyAll();
			host.reallocateMigratingInVms();
		}
		for (Map<String, Object> map : getSavedAllocation()) {
			Vm vm = (Vm) map.get("vm");
			PowerHost host = (PowerHost) map.get("host");
			if (!host.vmCreate(vm)) {
				Log.printLine("Couldn't restore VM #" + vm.getId() + " on host #" + host.getId());
				System.exit(0);
			}
			getVmTable().put(vm.getUid(), host);
		}
	}

	/**
	 * 虚拟机分配消耗的能耗Gets the power after allocation.
	 * 
	 * @param host the host
	 * @param vm the vm
	 * 
	 * @return the power after allocation
	 */
	protected double getPowerAfterAllocation(PowerHost host, Vm vm) {
		double power = 0;
		try {// 通过
			power = host.getPowerModel().getPower(getMaxUtilizationCpuAfterAllocation(host, vm),getMaxUtilizationMemAfterAllocation(host, vm));
//			power = host.getPowerModel().getPower(getMaxUtilizationAfterAllocation(host, vm));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		return power;
	}

	/**
	 * Gets the power after allocation. We assume that load is balanced between PEs. The only
	 * restriction is: VM's max MIPS < PE's MIPS
	 * 
	 * @param host the host
	 * @param vm the vm
	 * 
	 * @return the power after allocation
	 */
	protected double getMaxUtilizationAfterAllocation(PowerHost host, Vm vm) {
		double requestedTotalMips = vm.getCurrentRequestedTotalMips();
		double hostUtilizationMips = getUtilizationOfCpuMips(host);
		double hostPotentialUtilizationMips = hostUtilizationMips + requestedTotalMips;
		double pePotentialUtilization = hostPotentialUtilizationMips / host.getTotalMips();
		return pePotentialUtilization;
	}
	
	/**
	 * Gets the power after allocation. We assume that load is balanced between PEs. The only
	 * restriction is: VM's max MIPS < PE's MIPS
	 * 
	 * @param host the host
	 * @param vm the vm
	 * 
	 * @return the power after allocation
	 */
	protected double getMaxUtilizationCpuAfterAllocation(PowerHost host, Vm vm) {
		double requestedTotalMips = vm.getCurrentRequestedTotalMips();
		double hostUtilizationMips = host.getUtilizationOfCpuMips();
		for (Vm vm2 : host.getVmList()) {
			if (host.getVmsMigratingIn().contains(vm2)) {
				// calculate additional potential CPU usage of a migrating in VM
				// 只分配了10％的计算能力，所以要分配回来
				hostUtilizationMips += host.getTotalAllocatedMipsForVm(vm2) * 0.9 / 0.1;
			}
		}
		double hostPotentialUtilizationMips = hostUtilizationMips + requestedTotalMips;
		double pePotentialUtilization = hostPotentialUtilizationMips / host.getTotalMips();
		return pePotentialUtilization;
	}
	
	protected double getMaxUtilizationMemAfterAllocation(PowerHost host, Vm vm) {
		double requestedTotalRam = vm.getCurrentRequestedRam();
		double hostUtilizationRam = host.getUtilizationOfRam();

		for (Vm vm2 : host.getVmList()) {
			if (host.getVmsMigratingIn().contains(vm2)) {
				// calculate additional potential CPU usage of a migrating in VM
				// 没有针对迁移机器预分配
				hostUtilizationRam += vm.getCurrentAllocatedRam();
			}
		}
		double hostPotentialUtilizationRam = hostUtilizationRam + requestedTotalRam;
		double ramPotentialUtilization = hostPotentialUtilizationRam / host.getRam();
		return ramPotentialUtilization;
	}
	/**
	 * Gets the utilization of the CPU in MIPS for the current potentially allocated VMs.
	 *
	 * @param host the host
	 *
	 * @return the utilization of the CPU in MIPS
	 */
	protected double getUtilizationOfCpuMips(PowerHost host) {
		double hostUtilizationMips = 0;
		for (Vm vm2 : host.getVmList()) {
			if (host.getVmsMigratingIn().contains(vm2)) {
				// calculate additional potential CPU usage of a migrating in VM
				hostUtilizationMips += host.getTotalAllocatedMipsForVm(vm2) * 0.9 / 0.1;
			}
			hostUtilizationMips += host.getTotalAllocatedMipsForVm(vm2);
		}
		return hostUtilizationMips;
	}

	/**
	 * Gets the saved allocation.
	 * 
	 * @return the saved allocation
	 */
	protected List<Map<String, Object>> getSavedAllocation() {
		return savedAllocation;
	}

	/**
	 * Sets the vm selection policy.
	 * 
	 * @param vmSelectionPolicy the new vm selection policy
	 */
	protected void setVmSelectionPolicy(PowerVmSelectionPolicy vmSelectionPolicy) {
		this.vmSelectionPolicy = vmSelectionPolicy;
	}

	/**
	 * Gets the vm selection policy.
	 * 
	 * @return the vm selection policy
	 */
	protected PowerVmSelectionPolicy getVmSelectionPolicy() {
		return vmSelectionPolicy;
	}

	/**
	 * Gets the utilization history.
	 * 
	 * @return the utilization history
	 */
	public Map<Integer, List<Double>> getUtilizationHistory() {
		return utilizationHistory;
	}

	/**
	 * Gets the metric history.
	 * 
	 * @return the metric history
	 */
	public Map<Integer, List<Double>> getMetricHistory() {
		return metricHistory;
	}

	/**
	 * Gets the time history.
	 * 
	 * @return the time history
	 */
	public Map<Integer, List<Double>> getTimeHistory() {
		return timeHistory;
	}

	/**
	 * Gets the execution time history vm selection.
	 * 
	 * @return the execution time history vm selection
	 */
	public List<Double> getExecutionTimeHistoryVmSelection() {
		return executionTimeHistoryVmSelection;
	}

	/**
	 * Gets the execution time history host selection.
	 * 
	 * @return the execution time history host selection
	 */
	public List<Double> getExecutionTimeHistoryHostSelection() {
		return executionTimeHistoryHostSelection;
	}

	/**
	 * Gets the execution time history vm reallocation.
	 * 
	 * @return the execution time history vm reallocation
	 */
	public List<Double> getExecutionTimeHistoryVmReallocation() {
		return executionTimeHistoryVmReallocation;
	}

	/**
	 * Gets the execution time history total.
	 * 
	 * @return the execution time history total
	 */
	public List<Double> getExecutionTimeHistoryTotal() {
		return executionTimeHistoryTotal;
	}

}
