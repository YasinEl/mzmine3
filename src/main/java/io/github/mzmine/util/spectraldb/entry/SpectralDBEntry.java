/*
 * Copyright 2006-2022 The MZmine Development Team
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

package io.github.mzmine.util.spectraldb.entry;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.compoundannotations.CompoundDBAnnotation;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.datamodel.features.types.annotations.InChIKeyStructureType;
import io.github.mzmine.datamodel.features.types.annotations.InChIStructureType;
import io.github.mzmine.datamodel.features.types.annotations.SmilesStructureType;
import io.github.mzmine.datamodel.features.types.annotations.formula.FormulaType;
import io.github.mzmine.datamodel.features.types.annotations.iin.IonTypeType;
import io.github.mzmine.datamodel.features.types.numbers.CCSType;
import io.github.mzmine.datamodel.features.types.numbers.ChargeType;
import io.github.mzmine.datamodel.features.types.numbers.NeutralMassType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.datamodel.impl.MSnInfoImpl;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.datamodel.msms.DDAMsMsInfo;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import io.github.mzmine.util.ParsingUtils;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public class SpectralDBEntry {

  private static final Logger logger = Logger.getLogger(SpectralDBEntry.class.getName());
  public static final String XML_ELEMENT = "spectraldatabaseentry";
  public static final String XML_DB_FIELD_LIST_ELEMENT = "databasefieldslist";
  private static final String XML_DB_FIELD_ELEMENT = "entry";
  private static final String XML_FIELD_NAME_ATTR = "name";

  private final Map<DBEntryField, Object> fields;
  private final DataPoint[] dps;

  public SpectralDBEntry(double precursorMZ, DataPoint[] dps) {
    this.fields = new HashMap<>();
    this.dps = dps;
    fields.put(DBEntryField.MZ, precursorMZ);
    fields.put(DBEntryField.NUM_PEAKS, dps.length);
  }

  public SpectralDBEntry(double precursorMZ, int charge, DataPoint[] dps) {
    this(precursorMZ, dps);
    fields.put(DBEntryField.CHARGE, charge);
  }

  public SpectralDBEntry(Map<DBEntryField, Object> fields, DataPoint[] dps) {
    this.fields = fields;
    this.dps = dps;
  }

  public SpectralDBEntry(final Scan scan, final CompoundDBAnnotation match,
      final DataPoint[] dataPoints) {
    this(Objects.requireNonNullElse(match.getPrecursorMZ(), scan.getPrecursorMz()), dataPoints);
    // scan details
    putIfNotNull(DBEntryField.CHARGE, scan.getPrecursorCharge());
    putIfNotNull(DBEntryField.ION_MODE, scan.getPolarity());

    MsMsInfo msMsInfo = scan.getMsMsInfo();
    if (msMsInfo instanceof MSnInfoImpl msnInfo) {
      List<DDAMsMsInfo> precursors = msnInfo.getPrecursors();
      putIfNotNull(DBEntryField.MSN_COLLISION_ENERGIES,
          extractJsonList(precursors, DDAMsMsInfo::getActivationEnergy));
      putIfNotNull(DBEntryField.MSN_PRECURSOR_MZS,
          extractJsonList(precursors, DDAMsMsInfo::getIsolationMz));
      putIfNotNull(DBEntryField.MSN_FRAGMENTATION_METHODS,
          extractJsonList(precursors, DDAMsMsInfo::getActivationMethod));
      putIfNotNull(DBEntryField.MSN_ISOLATION_WINDOWS,
          extractJsonList(precursors, DDAMsMsInfo::getIsolationWindow));
      putIfNotNull(DBEntryField.MS_LEVEL, msnInfo.getMsLevel());
    } else if (msMsInfo != null) {
      putIfNotNull(DBEntryField.COLLISION_ENERGY, msMsInfo.getActivationEnergy());
      putIfNotNull(DBEntryField.FRAGMENTATION_METHOD, msMsInfo.getActivationMethod());
      putIfNotNull(DBEntryField.ISOLATION_WINDOW, msMsInfo.getIsolationWindow());
      putIfNotNull(DBEntryField.MS_LEVEL, msMsInfo.getMsLevel());
    }

    // transfer match to fields
    for (var entry : match.getReadOnlyMap().entrySet()) {
      DBEntryField field = switch (entry.getKey()) {
        case RTType ignored -> DBEntryField.RT;
        case CompoundNameType ignored -> DBEntryField.NAME;
        case FormulaType ignored -> DBEntryField.FORMULA;
        case SmilesStructureType ignored -> DBEntryField.SMILES;
        case InChIStructureType ignored -> DBEntryField.INCHI;
        case InChIKeyStructureType ignored -> DBEntryField.INCHIKEY;
        case CCSType ignored -> DBEntryField.CCS;
        case ChargeType ignored -> DBEntryField.CHARGE;
        case NeutralMassType ignored -> DBEntryField.MOLWEIGHT;
        case CommentType ignored -> DBEntryField.COMMENT;
        case IonTypeType ignored -> DBEntryField.ION_TYPE;
//        case SynonymType ignored -> DBEntryField.SYNONYM;
        default -> null;
      };
      try {
        putIfNotNull(field, entry.getValue());
      } catch (Exception ex) {
        logger.log(Level.WARNING,
            "Types were not converted from DB match to DB entry " + ex.getMessage(), ex);
      }
    }
  }

  private static String extractJsonList(final List<DDAMsMsInfo> precursors,
      Function<DDAMsMsInfo, Object> extractor) {
    String list = precursors.stream().map(extractor).filter(Objects::nonNull).map(Objects::toString)
        .collect(Collectors.joining(","));
    if (list.isEmpty()) {
      return null;
    }
    return "[" + list + "]";
  }

  public boolean putIfNotNull(DBEntryField field, Object value) {
    if (field != null && value != null) {
      fields.put(field, value);
      return true;
    }
    return false;
  }

  public static SpectralDBEntry loadFromXML(XMLStreamReader reader) throws XMLStreamException {
    if (!(reader.isStartElement() && reader.getLocalName().equals(XML_ELEMENT))) {
      throw new IllegalStateException(
          "Cannot load spectral db entry from the current element. Wrong name.");
    }

    double[] mzs = null;
    double[] intensities = null;
    Map<DBEntryField, Object> fields = null;

    while (reader.hasNext() && !(reader.isEndElement() && reader.getLocalName()
        .equals(XML_ELEMENT))) {
      reader.next();
      if (!reader.isStartElement()) {
        continue;
      }
      switch (reader.getLocalName()) {
        case CONST.XML_MZ_VALUES_ELEMENT ->
            mzs = ParsingUtils.stringToDoubleArray(reader.getElementText());
        case CONST.XML_INTENSITY_VALUES_ELEMENT ->
            intensities = ParsingUtils.stringToDoubleArray(reader.getElementText());
        case XML_DB_FIELD_LIST_ELEMENT -> fields = loadDBEntriesFromXML(reader);
        default -> {
        }
      }
    }

    assert mzs != null && intensities != null;
    assert mzs.length == intensities.length;

    DataPoint[] dps = new DataPoint[mzs.length];
    for (int i = 0; i < dps.length; i++) {
      dps[i] = new SimpleDataPoint(mzs[i], intensities[i]);
    }

    return new SpectralDBEntry(fields, dps);
  }

  private static Map<DBEntryField, Object> loadDBEntriesFromXML(XMLStreamReader reader)
      throws XMLStreamException {
    if (!(reader.isStartElement() && reader.getLocalName().equals(XML_DB_FIELD_LIST_ELEMENT))) {
      throw new IllegalStateException(
          "Cannot load spectral db entry fields from the current element. Wrong name.");
    }

    Map<DBEntryField, Object> fields = new EnumMap<>(DBEntryField.class);
    while (reader.hasNext() && !(reader.isEndElement() && reader.getLocalName()
        .equals(XML_DB_FIELD_LIST_ELEMENT))) {
      reader.next();
      if (!reader.isStartElement()) {
        continue;
      }

      final String keyName = reader.getAttributeValue(null, XML_FIELD_NAME_ATTR);
      final String value = reader.getElementText();

      DBEntryField field = DBEntryField.valueOf(keyName);
      Object convertValue = field.convertValue(value);

      fields.put(field, convertValue);
    }

    return fields;
  }

  public Double getPrecursorMZ() {
    return (Double) fields.get(DBEntryField.MZ);
  }

  public Optional<Object> getField(DBEntryField f) {
    return Optional.ofNullable(fields.get(f));
  }

  public <T> T getOrElse(DBEntryField f, T defaultValue) {
    final Object value = fields.get(f);
    return value == null ? defaultValue : (T) value;
  }

  public DataPoint[] getDataPoints() {
    return dps;
  }

  public void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement(XML_ELEMENT);

    double[] mzs = Arrays.stream(dps).mapToDouble(DataPoint::getMZ).toArray();
    double[] intensities = Arrays.stream(dps).mapToDouble(DataPoint::getIntensity).toArray();

    writer.writeStartElement(CONST.XML_MZ_VALUES_ELEMENT);
    writer.writeCharacters(ParsingUtils.doubleArrayToString(mzs, mzs.length));
    writer.writeEndElement(); // mzs
    writer.writeStartElement(CONST.XML_INTENSITY_VALUES_ELEMENT);
    writer.writeCharacters(ParsingUtils.doubleArrayToString(intensities, intensities.length));
    writer.writeEndElement(); // intensities

    writer.writeStartElement(XML_DB_FIELD_LIST_ELEMENT);
    for (Entry<DBEntryField, Object> entry : fields.entrySet()) {
      var key = entry.getKey();
      var value = entry.getValue();
      writer.writeStartElement(XML_DB_FIELD_ELEMENT);
      writer.writeAttribute(XML_FIELD_NAME_ATTR, key.name());
      writer.writeCharacters(String.valueOf(value));
      writer.writeEndElement(); // field
    }
    writer.writeEndElement(); // list

    writer.writeEndElement(); // this entry
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SpectralDBEntry that = (SpectralDBEntry) o;
    boolean b1 = Arrays.equals(dps, that.dps);
    boolean b2 = Objects.equals(fields, that.fields);
    return b1 && b2;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(fields);
    result = 31 * result + Arrays.hashCode(dps);
    return result;
  }
}
