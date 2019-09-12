/*
 * Remapper
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.remapper.json;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.minecraftforge.remapper.MappingDownloader;

public class Config {
    public int spec;

    public static int getSpec(InputStream stream) throws IOException {
        return MappingDownloader.GSON.fromJson(new InputStreamReader(stream), Config.class).spec;
    }
    public static int getSpec(byte[] data) throws IOException {
        return getSpec(new ByteArrayInputStream(data));
    }
}
