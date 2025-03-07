package org.dmfs.gradle.gver.tasks;

import org.dmfs.gver.dsl.GitVersionConfig;
import org.dmfs.gver.dsl.Strategy;
import org.dmfs.gver.git.changetypefacories.condition.CommitMessage;
import org.dmfs.gver.git.predicates.Contains;
import org.dmfs.jems2.Procedure;
import org.dmfs.jems2.function.Unchecked;
import org.dmfs.jems2.iterable.Mapped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.dmfs.gradle.gver.utils.Matchers.given;
import static org.dmfs.gradle.gver.utils.Tools.withRepository;
import static org.dmfs.gradle.gver.utils.Tools.withTempFolder;
import static org.dmfs.gver.git.ChangeType.*;
import static org.dmfs.jems2.hamcrest.matchers.LambdaMatcher.having;
import static org.dmfs.jems2.hamcrest.matchers.procedure.ProcedureMatcher.processes;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;


class TagReleaseTaskTest
{
    @Test
    void testTagMain()
    {
        assertThat((Procedure<Project>) project -> {
                try
                {
                    ((TagReleaseTask) project.getTasks().getByName("gitTagRelease")).perform();
                }
                catch (Exception e)
                {
                    throw new AssertionError("Unexpected Exception", e);
                }
            },
            withTempFolder(tempDir ->
                withRepository(getClass().getClassLoader().getResource("0.0.2-alpha.1.bundle"),
                    tempDir,
                    "main",
                    repository ->
                        given(
                            () ->
                            {
                                Project p = ProjectBuilder.builder().withProjectDir(tempDir).build();
                                p.getPluginManager().apply("org.dmfs.gver");
                                ((GitVersionConfig) p.getExtensions().getByName("gver")).mChangeTypeStrategy = new Strategy();
                                ((GitVersionConfig) p.getExtensions().getByName("gver")).mChangeTypeStrategy.mChangeTypeStrategies.addAll(
                                    asList(
                                        MAJOR.when(new CommitMessage(new Contains("#major"))),
                                        MINOR.when(new CommitMessage(new Contains("#minor"))),
                                        PATCH.when(new CommitMessage(new Contains("#patch"))),
                                        UNKNOWN.when(((repository1, commit, branches) -> true))));
                                return p;
                            },
                            project -> having(
                                "p",
                                proc -> repo -> proc.process(project),
                                processes(() -> repository,
                                    having(new Unchecked<Repository, Iterable<String>, Exception>(r -> new Mapped<>(Ref::getName, new Git(r).tagList().call())),
                                        containsInAnyOrder(R_TAGS + "0.0.1", R_TAGS + "0.0.2"))
                                ))))));

    }


    @Test
    void testTagFeature()
    {
        assertThat((Procedure<Project>) project -> {
                try
                {
                    ((TagReleaseTask) project.getTasks().getByName("gitTagRelease")).perform();
                }
                catch (Exception e)
                {
                    // we expect an Exception to be thrown
                }
            },
            withTempFolder(tempDir ->
                withRepository(getClass().getClassLoader().getResource("0.2.0-alpha.1.feature.bundle"),
                    tempDir,
                    "feature",
                    repository ->
                        given(
                            () ->
                            {
                                Project p = ProjectBuilder.builder().withProjectDir(tempDir).build();
                                p.getPluginManager().apply("org.dmfs.gver");
                                ((GitVersionConfig) p.getExtensions().getByName("gver")).mChangeTypeStrategy = new Strategy();
                                ((GitVersionConfig) p.getExtensions().getByName("gver")).mChangeTypeStrategy.mChangeTypeStrategies.addAll(
                                    asList(
                                        MAJOR.when(new CommitMessage(new Contains("#major"))),
                                        MINOR.when(new CommitMessage(new Contains("#minor"))),
                                        PATCH.when(new CommitMessage(new Contains("#patch"))),
                                        UNKNOWN.when(((repository1, commit, branches) -> true))));
                                return p;
                            },
                            project -> having(
                                "p",
                                proc -> repo -> proc.process(project),
                                processes(() -> repository,
                                    having(new Unchecked<Repository, Iterable<String>, Exception>(r -> new Mapped<>(Ref::getName, new Git(r).tagList().call())),
                                        containsInAnyOrder(R_TAGS + "0.0.1", R_TAGS + "0.1.0"))
                                ))))));

    }


    @Test
    void testSkipsExistingTag()
    {
        assertThat((Procedure<Project>) project -> {
                try
                {
                    ((TagReleaseTask) project.getTasks().getByName("gitTagRelease")).perform();
                }
                catch (Exception e)
                {
                    throw new AssertionError("Unexpected Exception", e);
                }
            },
            withTempFolder(tempDir ->
                withRepository(getClass().getClassLoader().getResource("0.2.0-trivial-change.bundle"),
                    tempDir,
                    "main",
                    repository ->
                        given(
                            () ->
                            {
                                Project p = ProjectBuilder.builder().withProjectDir(tempDir).build();
                                p.getPluginManager().apply("org.dmfs.gver");
                                ((GitVersionConfig) p.getExtensions().getByName("gver")).mChangeTypeStrategy = new Strategy();
                                ((GitVersionConfig) p.getExtensions().getByName("gver")).mChangeTypeStrategy.mChangeTypeStrategies.addAll(
                                    asList(
                                        NONE.when(new CommitMessage(new Contains("#none"))),
                                        MAJOR.when(new CommitMessage(new Contains("#major"))),
                                        MINOR.when(new CommitMessage(new Contains("#minor"))),
                                        PATCH.when(new CommitMessage(new Contains("#patch"))),
                                        UNKNOWN.when(((repository1, commit, branches) -> true))));
                                return p;
                            },
                            project -> having(
                                "p",
                                proc -> repo -> proc.process(project),
                                processes(() -> repository,
                                    having(new Unchecked<Repository, Iterable<String>, Exception>(r -> new Mapped<>(Ref::getName, new Git(r).tagList().call())),
                                        containsInAnyOrder(R_TAGS + "0.0.1", R_TAGS + "0.1.0", R_TAGS + "0.2.0"))
                                ))))));

    }
}