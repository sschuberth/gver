package org.dmfs.gver.git;

import org.dmfs.jems2.FragileFunction;
import org.dmfs.jems2.Function;
import org.dmfs.jems2.comparator.Reverse;
import org.dmfs.jems2.iterable.Mapped;
import org.dmfs.jems2.iterable.Seq;
import org.dmfs.jems2.iterable.Sorted;
import org.dmfs.jems2.optional.*;
import org.dmfs.jems2.single.Backed;
import org.dmfs.jems2.single.Unchecked;
import org.dmfs.semver.*;
import org.dmfs.semver.comparators.VersionComparator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.jgit.lib.Constants.R_TAGS;


public final class GitVersion implements FragileFunction<Repository, Version, Exception>
{
    private final ChangeTypeStrategy mStrategy;
    private final Suffixes mSuffixes;
    private final Function<String, String> mPreReleaseStrategy;


    public GitVersion(ChangeTypeStrategy strategy, Suffixes suffixes, Function<String, String> preReleaseStrategy)
    {
        mStrategy = strategy;
        mSuffixes = suffixes;
        mPreReleaseStrategy = preReleaseStrategy;
    }


    @Override
    public Version value(Repository repository) throws Exception
    {
        try (RevWalk revWalk = new RevWalk(repository))
        {
            RevCommit head = repository.parseCommit(repository.resolve("HEAD"));
            Version version = adjustForDirtyRepo(repository,
                readVersion(
                    repository,
                    revWalk,
                    head,
                    versions(repository),
                    mPreReleaseStrategy.value(repository.getBranch())));

            return new Backed<>(
                new Zipped<>(
                    version.preRelease(),
                    mSuffixes.suffix(repository, head, repository.getBranch()),
                    (current, suffix) -> new PreRelease(version, current + suffix)
                ),
                version).value();
        }
    }


    private Version adjustForDirtyRepo(Repository repository, Version version) throws GitAPIException
    {
        return !new Git(repository).diff().call().isEmpty() || !new Git(repository).diff().setCached(true).call().isEmpty()
            ? new NextPreRelease(version)
            : version;
    }


    private Version readVersion(Repository repository, RevWalk revWalk, RevCommit commit, Map<ObjectId, Version> tags, String preRelease)
    {
        return new Backed<>(
            new FirstPresent<>(
                new MapEntry<>(tags, commit.getId()),
                new First<>(
                    new Sorted<>(new Reverse<>(new VersionComparator()),
                        new Mapped<>(v -> mStrategy.changeType(repository, commit, new Unchecked<>(repository::getBranch).value()).value(v, preRelease),
                            new Mapped<>(commit1 -> readVersion(repository, revWalk, commit1, tags, preRelease),
                                new Seq<>(parsed(revWalk, commit).getParents())))))),
            () -> new PatchPreRelease(new Release(0, 0, 0), preRelease)).value();
    }


    private RevCommit parsed(RevWalk revWalk, RevCommit commit)
    {
        try
        {
            return revWalk.parseCommit(commit.getId());
        }
        catch (IOException e)
        {
            throw new RuntimeException("Can't parse commit", e);
        }
    }


    private Map<ObjectId, Version> versions(Repository repository) throws GitAPIException, IOException
    {
        VersionParser parser = new StrictParser();
        Map<ObjectId, Version> result = new HashMap<>();
        RefDatabase refDatabase = repository.getRefDatabase();

        for (Ref tag : new Git(repository).tagList().call())
        {
            try
            {
                String tagName = tag.getName().startsWith(R_TAGS) ? tag.getName().substring(R_TAGS.length()) : tag.getName();
                Version version = parser.parse(tagName);

                Ref peeled = refDatabase.peel(tag);
                ObjectId objectId = new Backed<>(new NullSafe<>(peeled.getPeeledObjectId()), peeled.getObjectId()).value();
                if (!result.containsKey(objectId) || new VersionComparator().compare(result.get(objectId), version) < 0)
                {
                    result.put(objectId, version);
                }
            }
            catch (IllegalArgumentException e)
            {
                // ignore non-version tags
            }
        }
        return result;
    }

}
