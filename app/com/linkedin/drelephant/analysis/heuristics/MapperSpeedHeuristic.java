package com.linkedin.drelephant.analysis.heuristics;

import java.util.ArrayList;
import java.util.List;

import com.linkedin.drelephant.analysis.Constants;
import com.linkedin.drelephant.analysis.Heuristic;
import com.linkedin.drelephant.analysis.HeuristicResult;
import com.linkedin.drelephant.analysis.Severity;
import com.linkedin.drelephant.hadoop.HadoopCounterHolder;
import com.linkedin.drelephant.hadoop.HadoopJobData;
import com.linkedin.drelephant.hadoop.HadoopTaskData;
import com.linkedin.drelephant.math.Statistics;

import org.apache.commons.io.FileUtils;

public class MapperSpeedHeuristic implements Heuristic {
  public static final String HEURISTIC_NAME = "Mapper Speed";

  @Override
  public String getHeuristicName() {
    return HEURISTIC_NAME;
  }

  @Override
  public HeuristicResult apply(HadoopJobData data) {

    HadoopTaskData[] tasks = data.getMapperData();

    List<Long> inputByteSizes = new ArrayList<Long>();
    List<Long> speeds = new ArrayList<Long>();
    List<Long> runtimesMs = new ArrayList<Long>();

    for (HadoopTaskData task : tasks) {
      if (task.timed()) {
        long inputBytes = task.getCounters().get(HadoopCounterHolder.CounterName.HDFS_BYTES_READ);
        long runtimeMs = task.getTotalRunTimeMs();
        inputByteSizes.add(inputBytes);
        runtimesMs.add(runtimeMs);
        //Speed is bytes per second
        speeds.add((1000 * inputBytes) / (runtimeMs));
      }
    }

    long medianSpeed = Statistics.median(speeds);
    long medianSize = Statistics.median(inputByteSizes);
    long medianRuntimeMs = Statistics.median(runtimesMs);

    Severity severity = getDiskSpeedSeverity(medianSpeed);

    //This reduces severity if task runtime is insignificant
    severity = Severity.min(severity, getRuntimeSeverity(medianRuntimeMs));

    HeuristicResult result = new HeuristicResult(HEURISTIC_NAME, severity);

    result.addDetail("Number of tasks", Integer.toString(tasks.length));
    result.addDetail("Median task input size", FileUtils.byteCountToDisplaySize(medianSize));
    result.addDetail("Median task runtime", Statistics.readableTimespan(medianRuntimeMs));
    result.addDetail("Median task speed", FileUtils.byteCountToDisplaySize(medianSpeed) + "/s");

    return result;
  }

  public static Severity getDiskSpeedSeverity(long speed) {
    return Severity.getSeverityDescending(speed, Constants.DISK_READ_SPEED / 2, Constants.DISK_READ_SPEED / 4,
        Constants.DISK_READ_SPEED / 8, Constants.DISK_READ_SPEED / 32);
  }

  public static Severity getRuntimeSeverity(long runtimeMs) {
    return Severity.getSeverityAscending(runtimeMs, 5 * Statistics.MINUTE_IN_MS, 10 * Statistics.MINUTE_IN_MS,
        15 * Statistics.MINUTE_IN_MS, 30 * Statistics.MINUTE_IN_MS);
  }
}
