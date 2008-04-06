/*
 * listFix() - Fix Broken Playlists!
 * Copyright (C) 2001-2008 Jeremy Caron
 * 
 * This file is part of listFix().
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, please see http://www.gnu.org/licenses/
 */

package listfix.comparators;

import listfix.model.*;

public class DescendingStatusComparator implements java.util.Comparator
{
    public int compare(Object a, Object b)
    {
        PlaylistEntry aa = (PlaylistEntry)a;
        PlaylistEntry bb = (PlaylistEntry)b;
        if (aa.getMessage().compareToIgnoreCase(bb.getMessage()) < 0)
        {
            return 1;
        }
        else if (aa.getMessage().equals(bb.getMessage()))
        {
            return 0;
        }
        return -1;
    }    
}

