/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultVersionConstraint;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.List;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector;

public class ModuleVersionSelectorSerializer implements Serializer<ModuleVersionSelector> {
    public ModuleVersionSelector read(Decoder decoder) throws IOException {
        String group = decoder.readString();
        String name = decoder.readString();
        VersionConstraint versionConstraint = readVersionConstraint(decoder);
        return newSelector(group, name, versionConstraint);
    }

    private VersionConstraint readVersionConstraint(Decoder decoder) throws IOException {
        String preferred = decoder.readString();
        int cpt = decoder.readSmallInt();
        List<String> rejects = Lists.newArrayListWithCapacity(cpt);
        for (int i = 0; i < cpt; i++) {
            rejects.add(decoder.readString());
        }
        return new DefaultVersionConstraint(preferred, rejects);
    }

    public void write(Encoder encoder, ModuleVersionSelector value) throws IOException {
        encoder.writeString(value.getGroup());
        encoder.writeString(value.getName());
        writeVersionConstraint(encoder, value.getVersionConstraint());
    }

    private void writeVersionConstraint(Encoder encoder, VersionConstraint cst) throws IOException {
        encoder.writeString(cst.getPreferredVersion());
        List<String> rejectedVersions = cst.getRejectedVersions();
        encoder.writeSmallInt(rejectedVersions.size());
        for (String rejectedVersion : rejectedVersions) {
            encoder.writeString(rejectedVersion);
        }
    }
}
