/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.dataprocessing.featdet_imsexpander;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.MobilityScanDataType;
import io.github.mzmine.datamodel.data_access.MobilityScanDataAccess;
import io.github.mzmine.datamodel.featuredata.FeatureDataUtils;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.FeatureDataType;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.exceptions.MissingMassListException;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImsExpanderSubTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(ImsExpanderSubTask.class.getName());

  protected final ParameterSet parameters;
  private final List<Frame> frames;
  private final ModularFeatureList flist;
  private List<ExpandingTrace> expandingTraces;

  private final AtomicInteger processedFrames = new AtomicInteger(0);
  private final Boolean useRawData;
  private final Double customNoiseLevel;
  private final Range<Double> traceMzRange;

  private long totalFrames = 1;
  private String desc;
  private final BinningMobilogramDataAccess mobilogramDataAccess;
  private final ModularFeatureList newFlist;
  private double createdRows = 0;

  public ImsExpanderSubTask(@Nullable final MemoryMapStorage storage,
      @NotNull final ParameterSet parameters, @NotNull final List<Frame> frames,
      @NotNull final ModularFeatureList flist, @NotNull final List<ExpandingTrace> expandingTraces,
      BinningMobilogramDataAccess mobilogramDataAccess, ModularFeatureList newFlist) {
    super(storage, Instant.now()); // just a subtask, date irrelevant
    this.parameters = parameters;
    this.frames = frames;
    this.flist = flist;
    this.expandingTraces = expandingTraces;
    this.useRawData = parameters.getParameter(ImsExpanderParameters.useRawData).getValue();
    this.customNoiseLevel = parameters.getParameter(ImsExpanderParameters.useRawData)
        .getEmbeddedParameter().getValue();
    traceMzRange = expandingTraces.size() > 0 ? Range.closed(
        expandingTraces.get(0).getMzRange().lowerEndpoint(),
        expandingTraces.get(expandingTraces.size() - 1).getMzRange().upperEndpoint())
        : Range.singleton(0d);

    desc = flist.getName() + ": expanding traces for frame " + processedFrames.get() + "/"
        + totalFrames + " m/z range: " + RangeUtils.formatRange(traceMzRange,
        MZmineCore.getConfiguration().getMZFormat());
    this.mobilogramDataAccess = mobilogramDataAccess;
    this.newFlist = newFlist;
  }

  @Override
  public String getTaskDescription() {
    return desc;
  }

  @Override
  public double getFinishedPercentage() {
    if (expandingTraces == null) {
      return 1.0d;
    }
    return (processedFrames.get() / (double) totalFrames) * 0.5
        + createdRows / (double) expandingTraces.size();
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    final IMSRawDataFile imsFile = (IMSRawDataFile) frames.get(0).getDataFile();
    logger.finest("Initialising data access for file " + imsFile.getName());
    final MobilityScanDataAccess access = new MobilityScanDataAccess(imsFile,
        useRawData ? MobilityScanDataType.RAW : MobilityScanDataType.CENTROID, frames);

    totalFrames = access.getNumberOfScans();
    final NumberFormat mzFormat = MZmineCore.getConfiguration().getMZFormat();

    final int numTraces = expandingTraces.size();
    try {

      for (int i = 0; i < access.getNumberOfScans(); i++) {
        if (isCanceled()) {
          // allow traces to be released
          expandingTraces = null;
          return;
        }

        final Frame frame = access.nextFrame();

        while (access.hasNextMobilityScan()) {
          final MobilityScan mobilityScan = access.nextMobilityScan();

          int traceIndex = 0;
          for (int dpIndex = 0; dpIndex < access.getNumberOfDataPoints() && traceIndex < numTraces;
              dpIndex++) {
            final double mz = access.getMzValue(dpIndex);
            final double intensity = access.getIntensityValue(dpIndex);

            if (useRawData && intensity < customNoiseLevel) {
              continue;
            }

            // while the trace upper mz smaller than the current mz, we increment the trace index
            while (expandingTraces.get(traceIndex).getMzRange().upperEndpoint() < mz
                && traceIndex < numTraces - 1) {
              traceIndex++;
            }
            // if the current lower mz passed the current data point, we go to the next data point
            if (expandingTraces.get(traceIndex).getMzRange().lowerEndpoint() > mz) {
              continue;
            }

            // try to offer the current data point to the trace
            while (expandingTraces.get(traceIndex).getMzRange().contains(mz)
                && !expandingTraces.get(traceIndex).offerDataPoint(access, dpIndex)
                && traceIndex < numTraces - 1) {
              traceIndex++;
            }
          }
        }
        processedFrames.getAndIncrement();

        desc = flist.getName() + ": expanding traces for frame " + processedFrames.get() + "/"
            + totalFrames + " m/z range: " + RangeUtils.formatRange(traceMzRange, mzFormat);
      }
    } catch (MissingMassListException e) {
      // allow traces to be released
      expandingTraces = null;
      e.printStackTrace();
      logger.log(Level.WARNING, e.getMessage(), e);
      setErrorMessage(e.getMessage());
      setStatus(TaskStatus.ERROR);
    }

    // create the new rows here, so the memory can be released after
    final List<FeatureListRow> newRows = new ArrayList<>(expandingTraces.size());
    for (var expandingTrace : expandingTraces) {
      desc = "Creating new features " + createdRows + "/" + expandingTraces.size();

      if (expandingTrace.getNumberOfMobilityScans() > 1) {
        final IonMobilogramTimeSeries series = expandingTrace.toIonMobilogramTimeSeries(
            getMemoryMapStorage(), mobilogramDataAccess);
        final ModularFeatureListRow row = new ModularFeatureListRow(newFlist,
            expandingTrace.getRow(), false);
        final ModularFeature f = new ModularFeature(newFlist,
            expandingTrace.getRow().getFeature(imsFile));
        f.set(FeatureDataType.class, series);
        row.addFeature(imsFile, f);
        FeatureDataUtils.recalculateIonSeriesDependingTypes(f);
        newRows.add(row);
      }

      createdRows++;

      if (isCanceled()) {
        // allow traces to be released
        expandingTraces = null;
        return;
      }
    }

    // allow traces to be released
    expandingTraces = null;

    synchronized (newFlist) {
      newRows.forEach(newFlist::addRow);
    }

    setStatus(TaskStatus.FINISHED);
  }
}
