/**
 * Copyright (C) 2010, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * AGPL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.ide.buildtools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import io.fabric8.insight.maven.aether.Aether;
import io.fabric8.insight.maven.aether.AetherResult;
import io.fabric8.insight.maven.aether.Repository;
import io.hawt.maven.indexer.ArtifactDTO;
import io.hawt.maven.indexer.MavenIndexerFacade;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Downloader {
    public static Logger LOG = LoggerFactory.getLogger(Downloader.class);

    private MavenIndexerFacade indexer;
    private Aether aether;
    private File archetypeDir = new File("fuse-ide-archetypes");
    private File camelComponentMetaData = new File("camel-metadata");
    private File xsdDir = new File("fuse-ide-xsds");
    private boolean delete = true;

    // setup an ignore list for unwanted archetypes
    private static ArrayList<String> ignoredArtifacts = new ArrayList<String>();

    static {
        ignoredArtifacts.add("camel-archetype-component-scala");
        ignoredArtifacts.add("camel-archetype-scala");
        ignoredArtifacts.add("camel-web-osgi-archetype");
        ignoredArtifacts.add("camel-archetype-groovy");
    }

    public static void main(String[] args) {
        try {
            // lets find the eclipse plugins directory
            File rs_editor = new File("../../editor/plugins");
            File rs_core = new File("../../core/plugins");
            if (args.length > 1) {
                rs_editor = new File(args[0]);
                rs_core = new File(args[1]);
            }
            LOG.info("Using editor plugins directory: {}", rs_editor.getAbsolutePath());
            LOG.info("Using core plugins directory: {}", rs_core.getAbsolutePath());

            if (!rs_editor.exists()) {
                fail("IDE editor plugins directory does not exist!");
            }
            if (!rs_editor.isDirectory()) {
                fail("IDE editor plugins directory is a file, not a directory!");
            }
            if (!rs_core.exists()) {
                fail("IDE core plugins directory does not exist!");
            }
            if (!rs_core.isDirectory()) {
                fail("IDE core plugins directory is a file, not a directory!");
            }

            File archetypesDir = new File(rs_editor, "org.fusesource.ide.branding/archetypes");
            File xsdsDir = new File(rs_editor, "org.fusesource.ide.catalogs");
            File compDir = new File(rs_core, "org.fusesource.ide.camel.model/components");

            Downloader app = new Downloader(archetypesDir, xsdsDir, compDir);
            app.start();
            LOG.info("Indexer has started, now trying to find stuff");
            app.run();
            app.stop();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    protected static void fail(String message) {
        LOG.error(message);
        System.exit(1);
    }

    public Downloader() {
    }

    public Downloader(File archetypeDir, File xsdDir, File camelComponentMetaData) {
        this.archetypeDir = archetypeDir;
        this.xsdDir = xsdDir;
        this.camelComponentMetaData = camelComponentMetaData;
    }

    public static File targetDir() {
        String basedir = System.getProperty("basedir", ".");
        return new File(basedir + "/target");
    }

    public void start() throws Exception {
        indexer = new MavenIndexerFacade();
        String[] repositories = { "http://repository.jboss.org/nexus/content/groups/ea/", "http://repo1.maven.org/maven2" };
        indexer.setRepositories(repositories);
        indexer.setCacheDirectory(new File(targetDir(), "mavenIndexer"));
        indexer.start();

        List<Repository> repos = Aether.defaultRepositories();
        repos.add(new Repository("ea.repository.jboss.org", "http://repository.jboss.org/nexus/content/groups/ea"));
        aether = new Aether(Aether.USER_REPOSITORY, repos);
    }

    public void stop() throws Exception {
        indexer.destroy();
    }

    public void run() throws Exception {
        downloadArchetypes();
        downloadXsds();
        downloadCamelComponentData();
    }

    public void downloadArchetypes() throws IOException {
        if (delete) {
            FileUtils.deleteDirectory(archetypeDir);
            archetypeDir.mkdirs();
        }

        PrintWriter out = new PrintWriter(new FileWriter(new File(archetypeDir, "archetypes.xml")));
        out.println("<archetypes>");

        try {
            downloadArchetypesForGroup(out, "org.apache.camel.archetypes", System.getProperty("camel-version"));
            downloadArchetypesForGroup(out, "org.apache.cxf.archetype", System.getProperty("cxf-version"));
            downloadArchetypesForGroup(out, "io.fabric8", System.getProperty("fabric-version"));
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            out.println("</archetypes>");
            out.close();
        }

        LOG.info("Running git add...");
        ProcessBuilder pb = new ProcessBuilder("git", "add", "*");
        pb.directory(archetypeDir);
        pb.start();
    }

    protected void downloadArchetypesForGroup(PrintWriter out, String groupId, String version)
            throws Exception {
        String classifier = null;
        String packaging = "maven-archetype";

        List<ArtifactDTO> answer = indexer.search(groupId, "", "", packaging, classifier, null);
        for (ArtifactDTO artifact : answer) {
            if (ignoredArtifacts.contains(artifact.getArtifactId())) {
                LOG.debug("Ignored: {}", artifact.getArtifactId());
                continue;
            }
            out.println("<archetype groupId='" + artifact.getGroupId() + "' artifactId='" + artifact.getArtifactId() + "' version='" + version + "'>" + artifact.getDescription() + "</archetype>");
            downloadArtifact(artifact, version);
        }
        LOG.debug("Found " + answer.size() + " results for groupId " + groupId + ", version " + version);
    }

    public void downloadXsds() throws Exception {
        // TODO can't seem to find the XSDs in the nexus index! No idea why! We find 2 out of the 8 schemas we need
        // so lets keep the scala code for this part
        new DownloadLatestXsds(xsdDir, true).run();
/*

        String[] groupIds = {"org.apache.camel.archetypes", "org.apache.cxf.archetype", "org.fusesource.fabric"};
        String classifier = null;
        String packaging =  "xsd";
        String groupId = "org.apache.camel";
        String artifactId = "camel-spring";
        List<ArtifactDTO> answer = indexer.search(groupId, artifactId, null, packaging, classifier);
        for (ArtifactDTO artifact : answer) {
            System.out.println("Found: " + artifact);
        }
        System.out.println("Found " + answer.size() + " results for groupId " + groupId);
*/
    }

    public void downloadCamelComponentData() throws IOException {
        if (delete) {
            FileUtils.deleteDirectory(camelComponentMetaData);
            camelComponentMetaData.mkdirs();
        }

        String version = System.getProperty("camel-version");

        PrintWriter out = new PrintWriter(new FileWriter(new File(camelComponentMetaData, "components-" + version + ".xml")));
        out.println("<connectors>");

        try {
            List<ArtifactDTO> answer = indexer.search("org.apache.camel", "", version, "jar", "", null);

            for (ArtifactDTO artifact : answer) {
                if (!artifact.getArtifactId().startsWith("camel-")) {
                    LOG.debug("Ignored: {}", artifact.getArtifactId());
                    continue;
                }

                String[] components = downloadProperties(artifact, artifact.getVersion());
                if (components.length > 0) {
                    out.println("   <connector id='" + artifact.getArtifactId().substring(artifact.getArtifactId().indexOf("-") + 1) + "'>");
                    out.println("       <protocols>");
                    for (String component : components) {
                        out.println("           <protocol>" + component + "</protocol>");
                    }
                    out.println("      </protocols>");
                    out.println("       <dependency>");
                    out.println(String.format("         <groupId>%s</groupId>", artifact.getGroupId()));
                    out.println(String.format("         <artifactId>%s</artifactId>", artifact.getArtifactId()));
                    out.println(String.format("         <version>%s</version>", artifact.getVersion()));
                    out.println("       </dependency>");
                    out.println("   </connector>");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            out.println("</connectors>");
            out.close();
        }

        LOG.info("Running git add...");
        ProcessBuilder pb = new ProcessBuilder("git", "add", "*");
        pb.directory(camelComponentMetaData);
        pb.start();
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public Aether getAether() {
        return aether;
    }

    public void setAether(Aether aether) {
        this.aether = aether;
    }

    public MavenIndexerFacade getIndexer() {
        return indexer;
    }

    public void setIndexer(MavenIndexerFacade indexer) {
        this.indexer = indexer;
    }

    public File getArchetypeDir() {
        return archetypeDir;
    }

    public void setArchetypeDir(File archetypeDir) {
        this.archetypeDir = archetypeDir;
    }

    public File getXsdDir() {
        return xsdDir;
    }

    public void setXsdDir(File xsdDir) {
        this.xsdDir = xsdDir;
    }

    protected void downloadArtifact(ArtifactDTO artifact, String version) throws Exception {
        try {
            AetherResult result = aether.resolve(artifact.getGroupId(), artifact.getArtifactId(), version, "jar", null);
            if (result != null) {
                List<File> files = result.getResolvedFiles();
                if (files != null && files.size() > 0) {
                    File file = files.get(0);
                    //for (File file : files) {
                    File newFile = new File(archetypeDir, file.getName());
                    IOUtils.copy(new FileInputStream(file), new FileOutputStream(newFile));
                    LOG.info("Copied " + newFile.getPath());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    protected String[] downloadProperties(ArtifactDTO artifact, String version) {
        try {
            AetherResult result = aether.resolve(artifact.getGroupId(), artifact.getArtifactId(), version, "properties", "camelComponent");
            if (result != null) {
                List<File> files = result.getResolvedFiles();
                if (files != null && files.size() > 0) {
                    File file = files.get(0);
                    Properties p = new Properties();
                    p.load(new FileInputStream(file));
                    String comps = p.getProperty("components", "");
                    return comps.split(" ");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new String[0];
    }

}