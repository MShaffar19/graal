/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

public class NativeImageClassLoader extends URLClassLoader {

    public final List<Path> imagecp;
    public final List<Path> buildcp;

    NativeImageClassLoader(String[] classpath, ClassLoader parent) {
        super(verifyClassPathAndConvertToURLs(classpath), parent);

        imagecp = Collections.unmodifiableList(Arrays.stream(getURLs()).map(NativeImageClassLoader::urlToPath).collect(Collectors.toList()));

        ArrayList<Path> buildcp = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(Paths::get).collect(Collectors.toCollection(ArrayList::new));
        /* If the GraalVM SDK is on the boot class path, and it contains annotated types. */
        final String sunBootClassPath = System.getProperty("sun.boot.class.path");
        if (sunBootClassPath != null) {
            for (String s : sunBootClassPath.split(File.pathSeparator)) {
                if (s.contains("graal-sdk")) {
                    buildcp.add(Paths.get(s));
                }
            }
        }
        this.buildcp = buildcp;
    }

    private static URL[] verifyClassPathAndConvertToURLs(String[] classpath) {
        Stream<Path> pathStream = new LinkedHashSet<>(Arrays.asList(classpath)).stream().flatMap(NativeImageClassLoader::toClassPathEntries);
        return pathStream.map(v -> {
            try {
                return v.toAbsolutePath().toUri().toURL();
            } catch (MalformedURLException e) {
                throw UserError.abort("Invalid classpath element '" + v + "'. Make sure that all paths provided with '" + SubstrateOptions.IMAGE_CLASSPATH_PREFIX + "' are correct.");
            }
        }).toArray(URL[]::new);
    }

    static Stream<Path> toClassPathEntries(String classPathEntry) {
        Path entry = ClasspathUtils.stringToClasspath(classPathEntry);
        if (entry.endsWith(ClasspathUtils.cpWildcardSubstitute)) {
            try {
                return Files.list(entry.getParent()).filter(ClasspathUtils::isJar);
            } catch (IOException e) {
                return Stream.empty();
            }
        }
        if (Files.isReadable(entry)) {
            return Stream.of(entry);
        }
        return Stream.empty();
    }

    private static Path urlToPath(URL url) {
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw VMError.shouldNotReachHere();
        }
    }
}
