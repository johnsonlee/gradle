/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.VersionConstraintInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.AbstractVersionVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;

import java.util.Collections;
import java.util.List;

import static com.google.common.base.Strings.nullToEmpty;

public class DefaultVersionConstraint implements VersionConstraintInternal {
    private String prefer;
    private List<String> rejects;

    public DefaultVersionConstraint(String version, boolean strict) {
        this.prefer = version;
        if (strict) {
            doStrict();
        } else {
            this.rejects = Collections.emptyList();
        }
    }

    private void doStrict() {
        // When strict version is used, we need to parse the preferred selector early, in order to compute its complement.
        // Hopefully this shouldn't happen too often. If it happens to become a performance problem, we need to reconsider
        // how we compute the "reject" clause
        DefaultVersionSelectorScheme versionSelectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator());
        VersionSelector versionSelector = versionSelectorScheme.complementForRejection(getPreferredSelector(versionSelectorScheme));
        this.rejects = Collections.singletonList(((AbstractVersionVersionSelector)versionSelector).getSelector());
    }

    public DefaultVersionConstraint(String version, List<String> rejects) {
        this.prefer = nullToEmpty(version);
        this.rejects = rejects;
    }

    public DefaultVersionConstraint(String version) {
        this(version, false);
    }

    @Override
    public VersionSelector getPreferredSelector(VersionSelectorScheme versionSelectorScheme) {
        return versionSelectorScheme.parseSelector(prefer);
    }

    @Override
    public VersionSelector getRejectionSelector(VersionSelectorScheme versionSelectorScheme) {
        if (rejects.isEmpty()) {
            return null;
        }
        if (rejects.size() == 1) {
            return versionSelectorScheme.parseSelector(rejects.get(0));
        }
        throw new UnsupportedOperationException("Multiple rejects are not yet supported");
    }

    @Override
    public VersionConstraint normalize() {
        if (prefer == null) {
            return new DefaultVersionConstraint("", rejects);
        } else {
            return new DefaultVersionConstraint(prefer, rejects);
        }
    }

    @Override
    public String getPreferredVersion() {
        return prefer;
    }

    @Override
    public void prefer(String version) {
        this.prefer = version;
        this.rejects = Collections.emptyList();
    }

    @Override
    public void strictly(String version) {
        this.prefer = version;
        doStrict();
    }

    @Override
    public List<String> getRejectedVersions() {
       return rejects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultVersionConstraint that = (DefaultVersionConstraint) o;

        if (prefer != null ? !prefer.equals(that.prefer) : that.prefer != null) {
            return false;
        }
        return rejects != null ? rejects.equals(that.rejects) : that.rejects == null;
    }

    @Override
    public int hashCode() {
        int result = prefer != null ? prefer.hashCode() : 0;
        result = 31 * result + (rejects != null ? rejects.hashCode() : 0);
        return result;
    }
}
