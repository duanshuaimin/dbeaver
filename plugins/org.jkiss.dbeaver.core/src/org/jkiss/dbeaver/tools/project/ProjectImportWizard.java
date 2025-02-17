/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2015 Serge Rieder (serge@jkiss.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (version 2)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jkiss.dbeaver.tools.project;

import org.jkiss.dbeaver.Log;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.core.CoreMessages;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.model.connection.DBPDriverLibrary;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DBRRunnableWithProgress;
import org.jkiss.dbeaver.registry.*;
import org.jkiss.dbeaver.registry.driver.DriverDescriptor;
import org.jkiss.dbeaver.runtime.RuntimeUtils;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.utils.ContentUtils;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.IOUtils;
import org.jkiss.utils.xml.XMLException;
import org.jkiss.utils.xml.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProjectImportWizard extends Wizard implements IImportWizard {

    static final Log log = Log.getLog(ProjectImportWizard.class);

    private ProjectImportData data = new ProjectImportData();

    public ProjectImportWizard() {
	}

	@Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setWindowTitle(CoreMessages.dialog_project_import_wizard_title);
        setNeedsProgressMonitor(true);
    }

    @Override
    public void addPages() {
        super.addPages();
        addPage(new ProjectImportWizardPageFile(data));
        //addPage(new ProjectImportWizardPageFinal(data));
    }

	@Override
	public boolean performFinish() {
        try {
            RuntimeUtils.run(getContainer(), true, true, new DBRRunnableWithProgress() {
                @Override
                public void run(DBRProgressMonitor monitor) throws InvocationTargetException, InterruptedException
                {
                    try {
                        importProjects(monitor);
                    } catch (Exception e) {
                        throw new InvocationTargetException(e);
                    }
                }
            });
        }
        catch (InterruptedException ex) {
            return false;
        }
        catch (InvocationTargetException ex) {
            UIUtils.showErrorDialog(
                getShell(),
                "Import error",
                "Cannot import projects",
                ex.getTargetException());
            return false;
        }
        UIUtils.showMessageBox(getShell(), CoreMessages.dialog_project_import_wizard_message_success_import_title, CoreMessages.dialog_project_import_wizard_message_success_import_message, SWT.ICON_INFORMATION);
        return true;
	}

    private void importProjects(DBRProgressMonitor monitor) throws IOException, DBException
    {
        try (ZipFile zipFile = new ZipFile(data.getImportFile(), ZipFile.OPEN_READ)) {
            ZipEntry metaEntry = zipFile.getEntry(ExportConstants.META_FILENAME);
            if (metaEntry == null) {
                throw new DBException("Cannot find meta file");
            }
            final Map<String, String> libMap = new HashMap<>();
            final Map<String, String> driverMap = new HashMap<>();
            InputStream metaStream = zipFile.getInputStream(metaEntry);
            if (metaStream == null) {
                throw new DBException("Cannot open meta file '" + metaEntry.getName() + "'"); //$NON-NLS-2$
            }
            try {
                final Document metaDocument = XMLUtils.parseDocument(metaStream);
                {
                    // Read libraries map
                    final Element libsElement = XMLUtils.getChildElement(metaDocument.getDocumentElement(), ExportConstants.TAG_LIBRARIES);
                    if (libsElement != null) {
                        final Collection<Element> libList = XMLUtils.getChildElementList(libsElement, RegistryConstants.TAG_FILE);
                        monitor.beginTask(CoreMessages.dialog_project_import_wizard_monitor_load_libraries, libList.size());
                        for (Element libElement : libList) {
                            libMap.put(
                                libElement.getAttribute(ExportConstants.ATTR_PATH),
                                libElement.getAttribute(ExportConstants.ATTR_FILE));
                            monitor.worked(1);
                        }
                        monitor.done();
                    }
                }

                {
                    // Collect drivers to import
                    final Element driversElement = XMLUtils.getChildElement(metaDocument.getDocumentElement(), RegistryConstants.TAG_DRIVERS);
                    if (driversElement != null) {
                        final Collection<Element> driverList = XMLUtils.getChildElementList(driversElement, RegistryConstants.TAG_DRIVER);
                        monitor.beginTask(CoreMessages.dialog_project_import_wizard_monitor_import_drivers, driverList.size());
                        for (Element driverElement : driverList) {
                            if (monitor.isCanceled()) {
                                break;
                            }

                            importDriver(monitor, driverElement, zipFile, libMap, driverMap);
                            monitor.worked(1);
                        }
                        // Save drivers
                        DataSourceProviderRegistry.getInstance().saveDrivers();
                        monitor.done();
                    }
                }

                {
                    // Import projects
                    final Element projectsElement = XMLUtils.getChildElement(metaDocument.getDocumentElement(), ExportConstants.TAG_PROJECTS);
                    if (projectsElement != null) {
                        final Collection<Element> projectList = XMLUtils.getChildElementList(projectsElement, ExportConstants.TAG_PROJECT);
                        monitor.beginTask(CoreMessages.dialog_project_import_wizard_monitor_import_projects, projectList.size());
                        for (Element projectElement : projectList) {
                            if (monitor.isCanceled()) {
                                break;
                            }

                            importProject(monitor, projectElement, zipFile, driverMap);
                            monitor.worked(1);
                        }
                        monitor.done();
                    }
                }

            } catch (XMLException e) {
                throw new DBException("Cannot parse meta file", e);
            } catch (CoreException e) {
                throw new DBException("Cannot persist project", e);
            } finally {
                metaStream.close();
            }
        }
    }

    private DriverDescriptor importDriver(
        DBRProgressMonitor monitor,
        Element driverElement,
        ZipFile zipFile,
        Map<String, String> libMap,
        Map<String, String> driverMap) throws IOException, DBException
    {
        String providerId = driverElement.getAttribute(RegistryConstants.ATTR_PROVIDER);
        String driverId = driverElement.getAttribute(RegistryConstants.ATTR_ID);
        boolean isCustom = CommonUtils.getBoolean(driverElement.getAttribute(RegistryConstants.ATTR_CUSTOM));
        String driverCategory = driverElement.getAttribute(RegistryConstants.ATTR_CATEGORY);
        String driverName = driverElement.getAttribute(RegistryConstants.ATTR_NAME);
        String driverClass = driverElement.getAttribute(RegistryConstants.ATTR_CLASS);
        String driverURL = driverElement.getAttribute(RegistryConstants.ATTR_URL);
        String driverDefaultPort = driverElement.getAttribute(RegistryConstants.ATTR_PORT);
        String driverDescription = driverElement.getAttribute(RegistryConstants.ATTR_DESCRIPTION);

        DataSourceProviderDescriptor dataSourceProvider = DataSourceProviderRegistry.getInstance().getDataSourceProvider(providerId);
        if (dataSourceProvider == null) {
            throw new DBException("Cannot find data source provider '" + providerId + "' for driver '" + driverName + "'");
        }
        monitor.subTask(CoreMessages.dialog_project_import_wizard_monitor_load_driver + driverName);

        DriverDescriptor driver = null;
        if (!isCustom) {
            // Get driver by ID
            driver = dataSourceProvider.getDriver(driverId);
            if (driver == null) {
                log.warn("Driver '" + driverId + "' not found in data source provider '" + dataSourceProvider.getName() + "'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }
        if (driver == null) {
            // Try to find existing driver by class name
            List<DriverDescriptor> matchedDrivers = new ArrayList<>();
            for (DriverDescriptor tmpDriver : dataSourceProvider.getEnabledDrivers()) {
                if (tmpDriver.getDriverClassName().equals(driverClass)) {
                    matchedDrivers.add(tmpDriver);
                }
            }
            if (matchedDrivers.size() == 1) {
                driver = matchedDrivers.get(0);
            } else if (!matchedDrivers.isEmpty()) {
                // Multiple drivers with the same class - tru to find driver with the same sample URL or with the same name
                for (DriverDescriptor tmpDriver : matchedDrivers) {
                    if (tmpDriver.getSampleURL().equals(driverURL) || tmpDriver.getName().equals(driverName)) {
                        driver = tmpDriver;
                        break;
                    }
                }
                if (driver == null) {
                    // Not found - lets use first one
                    log.warn("Ambiguous driver '" + driverName + "' - multiple drivers with class '" + driverClass + "' found. First one will be used"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    driver = matchedDrivers.get(0);
                }
            }
        }
        if (driver == null) {
            // Create new driver
            driver = dataSourceProvider.createDriver();
            driver.setName(driverName);
            driver.setCategory(driverCategory);
            driver.setDescription(driverDescription);
            driver.setDriverClassName(driverClass);
            if (!CommonUtils.isEmpty(driverDefaultPort)) {
                driver.setDriverDefaultPort(driverDefaultPort);
            }
            driver.setSampleURL(driverURL);
            driver.setModified(true);
            dataSourceProvider.addDriver(driver);
        }

        // Parameters and properties
        for (Element libElement : XMLUtils.getChildElementList(driverElement, RegistryConstants.TAG_PARAMETER)) {
            driver.setDriverParameter(
                libElement.getAttribute(RegistryConstants.ATTR_NAME),
                libElement.getAttribute(RegistryConstants.ATTR_VALUE),
                false);
        }
        for (Element libElement : XMLUtils.getChildElementList(driverElement, RegistryConstants.TAG_PROPERTY)) {
            driver.setConnectionProperty(
                libElement.getAttribute(RegistryConstants.ATTR_NAME),
                libElement.getAttribute(RegistryConstants.ATTR_VALUE));
        }

        // Add libraries (only for managable drivers with empty library list)
        if (CommonUtils.isEmpty(driver.getDriverLibraries())) {
            List<String> libraryList = new ArrayList<>();
            for (Element libElement : XMLUtils.getChildElementList(driverElement, RegistryConstants.TAG_FILE)) {
                libraryList.add(libElement.getAttribute(RegistryConstants.ATTR_PATH));
            }

            for (String libPath : libraryList) {
                File libFile = new File(libPath);
                if (libFile.exists()) {
                    // Just use path as-is (may be it is local re-import or local environments equal to export environment)
                    driver.addDriverLibrary(libPath, DBPDriverLibrary.FileType.jar);
                } else {
                    // Get driver library from archive
                    String archiveLibEntry = libMap.get(libPath);
                    if (archiveLibEntry != null) {
                        ZipEntry libEntry = zipFile.getEntry(archiveLibEntry);
                        if (libEntry != null) {
                            // Extract driver to "drivers" folder
                            String libName = libFile.getName();
                            File contribFolder = DriverDescriptor.getDriversContribFolder();
                            if (!contribFolder.exists()) {
                                if (!contribFolder.mkdir()) {
                                    log.error("Cannot create drivers folder '" + contribFolder.getAbsolutePath() + "'"); //$NON-NLS-1$ //$NON-NLS-2$
                                    continue;
                                }
                            }
                            File importLibFile = new File(contribFolder, libName);
                            if (!importLibFile.exists()) {
                                FileOutputStream os = new FileOutputStream(importLibFile);
                                try {
                                    IOUtils.copyStream(zipFile.getInputStream(libEntry), os, IOUtils.DEFAULT_BUFFER_SIZE);
                                } finally {
                                    ContentUtils.close(os);
                                }
                            }
                            // Make relative path
                            String contribPath = contribFolder.getAbsolutePath();
                            String libAbsolutePath = importLibFile.getAbsolutePath();
                            String relativePath = libAbsolutePath.substring(contribPath.length());
                            while (relativePath.charAt(0) == '/' || relativePath.charAt(0) == '\\') {
                                relativePath = relativePath.substring(1);
                            }
                            driver.addDriverLibrary(relativePath, DBPDriverLibrary.FileType.jar);
                        }
                    }
                }
            }
        }

        // Update driver map
        driverMap.put(driverId, driver.getId());

        return driver;
    }

    private IProject importProject(DBRProgressMonitor monitor, Element projectElement, ZipFile zipFile, Map<String, String> driverMap)
        throws DBException, CoreException, IOException
    {
        String projectName = projectElement.getAttribute(ExportConstants.ATTR_NAME);
        String projectDescription = projectElement.getAttribute(ExportConstants.ATTR_DESCRIPTION);
        String targetProjectName = data.getTargetProjectName(projectName);
        if (targetProjectName == null) {
            targetProjectName = projectName;
        }

        IProject project = DBeaverCore.getInstance().getWorkspace().getRoot().getProject(targetProjectName);
        if (project.exists()) {
            throw new DBException("Project '" + targetProjectName + "' already exists");
        }

        monitor.subTask(CoreMessages.dialog_project_import_wizard_monitor_import_project + targetProjectName);

        final IProjectDescription description = DBeaverCore.getInstance().getWorkspace().newProjectDescription(project.getName());
        if (!CommonUtils.isEmpty(projectDescription)) {
            description.setComment(projectDescription);
        }
        ProjectRegistry projectRegistry = DBeaverCore.getInstance().getProjectRegistry();
        projectRegistry.projectBusy(project, true);
        project.create(description, 0, monitor.getNestedMonitor());

        try {
            // Open project
            project.open(monitor.getNestedMonitor());

            // Set project properties
            loadResourceProperties(monitor, project, projectElement);

            // Load resources
            importChildResources(
                monitor,
                project,
                projectElement,
                ExportConstants.DIR_PROJECTS + "/" + projectName + "/", //$NON-NLS-1$ //$NON-NLS-2$
                zipFile);

            // Update driver references in datasources
            updateDriverReferences(monitor, project, driverMap);

        } catch (Exception e) {
            // Cleanup project which was partially imported
            try {
                project.delete(true, true, monitor.getNestedMonitor());
            } catch (CoreException e1) {
                log.error(e1);
            }
            throw new DBException("Error importing project resources", e);
        } finally {
            projectRegistry.projectBusy(project, false);
        }
        projectRegistry.addProject(project);

        return project;
    }

    private void importChildResources(DBRProgressMonitor monitor, IContainer resource, Element resourceElement, String containerPath, ZipFile zipFile)
        throws DBException, IOException, CoreException
    {
        for (Element childElement : XMLUtils.getChildElementList(resourceElement, ExportConstants.TAG_RESOURCE)) {
            String childName = childElement.getAttribute(ExportConstants.ATTR_NAME);
            boolean isDirectory = CommonUtils.getBoolean(childElement.getAttribute(ExportConstants.ATTR_DIRECTORY));
            String entryPath = containerPath + childName;
            if (isDirectory) {
                entryPath += "/"; //$NON-NLS-1$
            }
            final ZipEntry resourceEntry = zipFile.getEntry(entryPath);
            if (resourceEntry == null) {
                throw new DBException("Project resource '" + entryPath + "' not found in archive");
            }
            if (isDirectory != resourceEntry.isDirectory()) {
                throw new DBException("Directory '" + entryPath + "' stored as file in archive");
            }
            IResource childResource;
            if (isDirectory) {
                IFolder folder;
                if (resource instanceof IFolder) {
                    folder = ((IFolder)resource).getFolder(childName);
                } else if (resource instanceof IProject) {
                    folder = ((IProject)resource).getFolder(childName);
                } else {
                    throw new DBException("Unsupported container type '" + resource.getClass().getName() + "'");
                }
                folder.create(true, true, monitor.getNestedMonitor());
                childResource = folder;
                importChildResources(monitor, folder, childElement, entryPath, zipFile);
            } else {
                IFile file;
                if (resource instanceof IFolder) {
                    file = ((IFolder)resource).getFile(childName);
                } else if (resource instanceof IProject) {
                    file = ((IProject)resource).getFile(childName);
                } else {
                    throw new DBException("Unsupported container type '" + resource.getClass().getName() + "'");
                }
                file.create(zipFile.getInputStream(resourceEntry), true, monitor.getNestedMonitor());
                childResource = file;
            }
            loadResourceProperties(monitor, childResource, childElement);
        }
    }

    private void loadResourceProperties(DBRProgressMonitor monitor, IResource resource, Element element) throws CoreException, IOException
    {
        if (resource instanceof IFile) {
            final String charset = element.getAttribute(ExportConstants.ATTR_CHARSET);
            if (!CommonUtils.isEmpty(charset)) {
                ((IFile) resource).setCharset(charset, monitor.getNestedMonitor());
            }
        }
        for (Element attrElement : XMLUtils.getChildElementList(element, ExportConstants.TAG_ATTRIBUTE)) {
            String qualifier = attrElement.getAttribute(ExportConstants.ATTR_QUALIFIER);
            String name = attrElement.getAttribute(ExportConstants.ATTR_NAME);
            String value = attrElement.getAttribute(ExportConstants.ATTR_VALUE);
            if (!CommonUtils.isEmpty(qualifier) && !CommonUtils.isEmpty(name) && !CommonUtils.isEmpty(value)) {
                resource.setPersistentProperty(new QualifiedName(qualifier, name), value);
            }
        }
    }

    private void updateDriverReferences(DBRProgressMonitor monitor, IProject project, Map<String, String> driverMap) throws DBException, CoreException, IOException
    {
        IFile configFile = project.getFile(DataSourceRegistry.CONFIG_FILE_NAME);
        if (configFile == null || !configFile.exists()) {
            configFile = project.getFile(DataSourceRegistry.OLD_CONFIG_FILE_NAME);
        }
        if (configFile == null || !configFile.exists()) {
            throw new DBException("Cannot find configuration file '" + DataSourceRegistry.CONFIG_FILE_NAME + "'");
        }
        // Read and filter datasources config
        final InputStream configContents = configFile.getContents();
        String filteredContent;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(configContents, GeneralUtils.DEFAULT_FILE_CHARSET));
            StringBuilder buffer = new StringBuilder();
            for (;;) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                buffer.append(line).append(GeneralUtils.getDefaultLineSeparator());
            }
            filteredContent = buffer.toString();
            for (Map.Entry<String, String> entry : driverMap.entrySet()) {
                if (!entry.getKey().equals(entry.getValue())) {
                    filteredContent = filteredContent.replace("driver=\"" + entry.getKey() + "\"", "driver=\"" + entry.getValue() + "\"");
                }
            }
        }
        finally {
            ContentUtils.close(configContents);
        }
        // Update configuration
        configFile.setContents(
            new ByteArrayInputStream(filteredContent.getBytes(GeneralUtils.DEFAULT_FILE_CHARSET)),
            true,
            false,
            monitor.getNestedMonitor());
    }


}
