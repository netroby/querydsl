/*
 * Copyright 2013, Mysema Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysema.query.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * CompilerMojo compiles the sources generated in the other tasks
 * 
 * @goal compile
 *
 */
public class CompileMojo extends AbstractMojo {
    
    private static final String JAVA_FILE_FILTER = "/*.java";
    private static final String[] ALL_JAVA_FILES_FILTER = new String[] { "**" + JAVA_FILE_FILTER };
    
    /**
     * @parameter expression="${project}" readonly=true required=true
     */
    private MavenProject project;

    /**
     * @parameter
     */
    private File targetFolder;
    
    /**
     * @parameter
     */
    private String sourceEncoding;

    /**
     * @parameter
     */
    private String source;
    
    /**
     * @parameter
     */
    private String target;
    
    /**
     * @parameter default-value=false
     */
    private boolean testClasspath;

    /**
     * @parameter
     */
    private Map<String, String> compilerOptions;
    
    /**
     * @component
     */
    private BuildContext buildContext;
    
    @SuppressWarnings("unchecked")
    private String buildCompileClasspath() {
        List<String> pathElements = null;
        try {
            if (testClasspath) {
                pathElements = project.getTestClasspathElements();
            } else {
                pathElements = project.getCompileClasspathElements();
            }
        } catch (DependencyResolutionRequiredException e) {
            getLog().warn("exception calling getCompileClasspathElements", e);
            return null;
        }

        if (pathElements.isEmpty()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pathElements.size(); i++) {
            if (i > 0) {
                result.append(File.pathSeparatorChar);
            }
            result.append(pathElements.get(i));                
        }
        return result.toString();
    }

    private List<String> getCompilerOptions() {
        Map<String, String> compilerOpts = new LinkedHashMap<String, String>();
        
        // classpath
        String cp = buildCompileClasspath();
        if (cp != null) {
            compilerOpts.put("cp", cp);    
        }        
        
        // No APT processing
        compilerOpts.put("proc:none", null);
        
        if (source != null) {
            compilerOpts.put("-source", source);
        }
        
        if (target != null) {
            compilerOpts.put("-target", target);
        }
        
        if (sourceEncoding != null) {
            compilerOpts.put("encoding", sourceEncoding);
        }
        
        if (testClasspath) {
            compilerOpts.put("d", project.getBuild().getTestOutputDirectory());
        } else {
            compilerOpts.put("d", project.getBuild().getOutputDirectory());
        } 
        
        if (compilerOptions != null) {
            compilerOpts.putAll(compilerOptions);    
        }
        
        List<String> opts = new ArrayList<String>(compilerOpts.size() * 2);

        for (Map.Entry<String, String> compilerOption : compilerOpts.entrySet()) {
            opts.add("-" + compilerOption.getKey());
            String value = compilerOption.getValue();
            if (value != null) {
                opts.add(value);
            }
        }
        return opts;
    }
    
    private Set<File> getJavaFiles(File directory) {
        String[] filters = ALL_JAVA_FILES_FILTER;
        
        Set<File> files = new HashSet<File>();
        // support for incremental build in m2e context
        Scanner scanner = buildContext.newScanner(directory);
        scanner.setIncludes(filters);
        scanner.scan();
        
        String[] includedFiles = scanner.getIncludedFiles();
        if (includedFiles != null) {
            for (String includedFile : includedFiles) {
                files.add(new File(scanner.getBasedir(), includedFile));
            }
        }
        return files;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager sjfm = jc.getStandardFileManager(null, null, null);
        try {
            Set<File> generatedFiles = getJavaFiles(targetFolder);
            Iterable<? extends JavaFileObject> fileObjects = sjfm.getJavaFileObjectsFromFiles(generatedFiles);
            List<String> opts = getCompilerOptions();                
            jc.getTask(null, null, null, opts, null, fileObjects).call();    
        } finally {
            try {
                sjfm.close();
            } catch (IOException e) {
                throw new MojoFailureException(e.getMessage(), e);
            }    
        }  
    }

}
