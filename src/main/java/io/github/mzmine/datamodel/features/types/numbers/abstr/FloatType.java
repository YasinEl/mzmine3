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

package io.github.mzmine.datamodel.features.types.numbers.abstr;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.exceptions.UndefinedRowBindingException;
import io.github.mzmine.datamodel.features.types.modifiers.BindingsFactoryType;
import io.github.mzmine.datamodel.features.types.modifiers.BindingsType;
import java.text.NumberFormat;
import java.util.Arrays;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FloatType extends NumberType<Property<Float>> implements BindingsFactoryType {

  protected FloatType(NumberFormat defaultFormat) {
    super(defaultFormat);
  }

  @Override
  @NotNull
  public String getFormattedString(@NotNull Property<Float> value) {
    if (value.getValue() == null) {
      return "";
    }
    return getFormatter().format(value.getValue().floatValue());
  }

  @Override
  public Property<Float> createProperty() {
    return new SimpleObjectProperty<Float>();
  }

  @Override
  public ObjectBinding<?> createBinding(BindingsType bind, ModularFeatureListRow row) {
    // get all properties of all features
    @SuppressWarnings("unchecked") Property<Float>[] prop = row.streamFeatures()
        .map(f -> (ModularFeature) f).map(f -> f.get(this)).toArray(Property[]::new);
    switch (bind) {
      case AVERAGE:
        return Bindings.createObjectBinding(() -> {
          float sum = 0;
          int n = 0;
          for (Property<Float> p : prop) {
            if (p.getValue() != null) {
              sum += p.getValue();
              n++;
            }
          }
          return n == 0 ? 0 : sum / n;
        }, prop);
      case MIN:
        return Bindings.createObjectBinding(() -> {
          float min = Float.POSITIVE_INFINITY;
          for (Property<Float> p : prop) {
            if (p.getValue() != null && p.getValue() < min) {
              min = p.getValue();
            }
          }
          return min;
        }, prop);
      case MAX:
        return Bindings.createObjectBinding(() -> {
          float max = Float.NEGATIVE_INFINITY;
          for (Property<Float> p : prop) {
            if (p.getValue() != null && p.getValue() > max) {
              max = p.getValue();
            }
          }
          return max;
        }, prop);
      case SUM:
        return Bindings.createObjectBinding(() -> {
          float sum = 0;
          for (Property<Float> p : prop) {
            if (p.getValue() != null) {
              sum += p.getValue();
            }
          }
          return sum;
        }, prop);
      case COUNT:
        return Bindings.createObjectBinding(() -> {
          return Arrays.stream(prop).filter(p -> p.getValue() != null).count();
        }, prop);
      case RANGE:
        return Bindings.createObjectBinding(() -> {
          Range<Float> result = null;
          for (Property<Float> p : prop) {
            if (p.getValue() != null) {
              if (result == null) {
                result = Range.singleton(p.getValue());
              } else {
                result = result.span(Range.singleton(p.getValue()));
              }
            }
          }
          return result;
        }, prop);
      default:
        throw new UndefinedRowBindingException(this, bind);
    }
  }

  @Override
  public void saveToXML(@NotNull XMLStreamWriter writer, @Nullable Object value,
      @NotNull ModularFeatureList flist, @NotNull ModularFeatureListRow row,
      @Nullable ModularFeature feature, @Nullable RawDataFile file) throws XMLStreamException {
    if (value == null) {
      return;
    }
    if (!(value instanceof Float)) {
      throw new IllegalArgumentException(
          "Wrong value type for data type: " + this.getClass().getName() + " value class: "
              + value.getClass());
    }
    writer.writeCharacters(String.valueOf(value));
  }

  @Override
  public Object loadFromXML(@NotNull XMLStreamReader reader, @NotNull ModularFeatureList flist,
      @NotNull ModularFeatureListRow row, @Nullable ModularFeature feature,
      @Nullable RawDataFile file) throws XMLStreamException {
    String str = reader.getElementText();
    if (str == null || str.isEmpty()) {
      return null;
    }
    return Float.parseFloat(str);
  }
}
