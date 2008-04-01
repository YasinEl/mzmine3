/*
 * Copyright 2006 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.io.util;

import net.sf.mzmine.data.Scan;


/**
 * Interface for classes that accept scans for processing, visualization etc.
 */
public interface RawDataAcceptor {

    /**
     * Process a scan
     * @param scan Scan to process 
     * @param index Index of this scan in the array of requested scans (0..total - 1)
     * @param total Total number of requested scans
     */
    void addScan(Scan scan, int index, int total);
    
}
