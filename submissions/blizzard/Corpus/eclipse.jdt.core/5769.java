/*******************************************************************************
 * Copyright (c) 2005, 2007 BEA Systems, Inc. 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    jgarms@bea.com - initial API and implementation
 *    
 *******************************************************************************/
package org.eclipse.jdt.apt.core.build;

import java.io.File;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Path;
import org.eclipse.jdt.apt.core.internal.build.Messages;

/**
 * Ant task for invoking the commandline apt builder
 *
 * Sample build.xml:
 * 
 * &lt;project name="test_eclipse" default="build" basedir="."&gt;
 * 
 *    &lt;taskdef name="apt" classname="org.eclipse.jdt.apt.core.build.JdtApt"/&gt;
 *
 *    &lt;target name="build"&gt;
 *        &lt;apt workspace="C:\my_workspace" eclipseHome="C:\eclipse"/&gt;
 *    &lt;/target&gt;
 * &lt;/project&gt;
 */
public class JdtApt extends Java {

    //$NON-NLS-1$
    private static final String APP_CLASSNAME = "org.eclipse.core.launcher.Main";

    //$NON-NLS-1$
    private static final String APP_PLUGIN = "org.eclipse.jdt.apt.core.aptBuild";

    private File workspace;

    private File startupJar;

    public void setWorkspace(File file) {
        if (!file.exists()) {
            throw new BuildException(Messages.JdtApt_noWorkspace + file);
        }
        workspace = file;
    }

    public void setEclipseHome(File file) {
        if (!file.exists()) {
            throw new BuildException(Messages.JdtApt_noEclipse + file);
        }
        //$NON-NLS-1$
        startupJar = new File(file, "startup.jar");
        if (!startupJar.exists()) {
            throw new BuildException(Messages.JdtApt_noStartupJar + file);
        }
    }

    public void execute() throws BuildException {
        if (workspace == null) {
            //$NON-NLS-1$
            throw new BuildException("Must set a workspace");
        }
        if (startupJar == null) {
            //$NON-NLS-1$
            throw new BuildException("Must set eclipse home");
        }
        setFork(true);
        setLogError(true);
        setClasspath(new Path(null, startupJar.getAbsolutePath()));
        setClassname(APP_CLASSNAME);
        //$NON-NLS-1$
        createArg().setValue("-noupdate");
        //$NON-NLS-1$
        createArg().setValue("-application");
        createArg().setValue(APP_PLUGIN);
        //$NON-NLS-1$
        createArg().setValue("-data");
        createArg().setValue(workspace.getAbsolutePath());
        super.execute();
    }
}