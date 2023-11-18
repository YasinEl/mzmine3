/*
 * Copyright (c) 2004-2023 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.group_imagecorrelate;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.impl.IonMobilitySupport;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.PercentParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.util.maths.similarity.SimilarityMeasure;
import org.jetbrains.annotations.NotNull;

public class ImageCorrelateGroupingParameters extends SimpleParameterSet {

  public static final FeatureListsParameter FEATURE_LISTS = new FeatureListsParameter();


  /**
   * Filter data points by minimum height
   */
  public static final DoubleParameter NOISE_LEVEL = new DoubleParameter(
      "Intensity threshold for correlation",
      "This intensity threshold is used to filter data points before feature shape correlation",
      MZmineCore.getConfiguration().getIntensityFormat(), 0d);

  public static final IntegerParameter MIN_NUMBER_OF_PIXELS = new IntegerParameter(
      "Minimum number of locations that must correlate",
      "Minimum number of locations that must correlate", 0);

  public static final OptionalParameter<IntegerParameter> MEDIAN_FILTER_WINDOW = new OptionalParameter<>(
      new IntegerParameter("Median filter window",
          "Smooth over pixels to reduce noise and remove outliers", 3, true), true);

  public static final OptionalParameter<DoubleParameter> QUANTILE_THRESHOLD = new OptionalParameter<>(
      new DoubleParameter("Ignore intensities not in quantile",
          "Only consider intensities above the selected percentile",
          MZmineCore.getConfiguration().getScoreFormat(), 0.5, 0.0, 1.0), true);

  public static final OptionalParameter<DoubleParameter> HOTSPOT_REMOVAL = new OptionalParameter<>(
      new DoubleParameter("Ignore very high intensity outliers",
          "Only consider values below the selected percentile, 0.99 is recommended",
          MZmineCore.getConfiguration().getScoreFormat(), 0.99, 0.0, 1.0), true);

  public static final ComboParameter<SimilarityMeasure> MEASURE = new ComboParameter<>("Measure",
      "Similarity measure", SimilarityMeasure.values(), SimilarityMeasure.PEARSON);

  public static final PercentParameter MIN_R = new PercentParameter("Min image correlation",
      "Minimum percentage for image correlation in one raw file.", 0.85, 0d, 1d);


  public static final OptionalParameter<StringParameter> SUFFIX = new OptionalParameter<>(
      new StringParameter("Suffix (or auto)", "Select suffix or deselect for auto suffix"), false);

  // Constructor
  public ImageCorrelateGroupingParameters() {
    super(FEATURE_LISTS, NOISE_LEVEL, MIN_NUMBER_OF_PIXELS, MEDIAN_FILTER_WINDOW,
        QUANTILE_THRESHOLD, HOTSPOT_REMOVAL, MEASURE, MIN_R, SUFFIX);
  }

  @Override
  public @NotNull IonMobilitySupport getIonMobilitySupport() {
    return IonMobilitySupport.SUPPORTED;
  }
}
