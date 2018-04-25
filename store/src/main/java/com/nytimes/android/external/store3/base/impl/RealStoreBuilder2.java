package com.nytimes.android.external.store3.base.impl;


import com.nytimes.android.external.store3.base.*;
import com.nytimes.android.external.store3.util.KeyParser;
import com.nytimes.android.external.store3.util.NoKeyParser;
import com.nytimes.android.external.store3.util.NoopParserFunc;
import com.nytimes.android.external.store3.util.NoopPersister;
import io.reactivex.Maybe;
import io.reactivex.Single;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;


/**
 * Builder where there parser is used.
 */
public class RealStoreBuilder2<Raw, Parsed, Key> {
    private final List<KeyParser> parsers = new ArrayList<>();
    private Persister<Parsed, Key> persister;
    private Fetcher<Raw, Key> fetcher;
    private MemoryPolicy memoryPolicy;

    @SuppressWarnings("PMD.UnusedPrivateField") //remove when it is implemented...
    private StalePolicy stalePolicy = StalePolicy.UNSPECIFIED;

    @Nonnull
    public static <Raw, Parsed, Key> RealStoreBuilder2<Raw, Parsed, Key> builder() {
        return new RealStoreBuilder2<>();
    }

    @Nonnull
    public RealStoreBuilder2<Raw, Parsed, Key> fetcher(final @Nonnull Fetcher<Raw, Key> fetcher) {
        this.fetcher = fetcher;
        return this;
    }

    @Nonnull
    public RealStoreBuilder2<Raw, Parsed, Key> persister(final @Nonnull Persister<Parsed, Key> persister) {
        this.persister = persister;
        return this;
    }

    @Nonnull
    public RealStoreBuilder2<Raw, Parsed, Key> persister(final @Nonnull DiskRead<Parsed, Key> diskRead,
                                                         final @Nonnull DiskWrite<Parsed, Key> diskWrite) {
        persister = new Persister<Parsed, Key>() {
            @Nonnull
            @Override
            public Maybe<Parsed> read(@Nonnull Key key) {
                return diskRead.read(key);
            }

            @Nonnull
            @Override
            public Single<Boolean> write(@Nonnull Key key, @Nonnull Parsed raw) {
                return diskWrite.write(key, raw);
            }
        };
        return this;
    }

    @Nonnull
    public RealStoreBuilder2<Raw, Parsed, Key> parser(final @Nonnull Parser<Raw, Parsed> parser) {
        this.parsers.clear();
        this.parsers.add(new NoKeyParser<>(parser));
        return this;
    }

    @Nonnull
    public RealStoreBuilder2<Raw, Parsed, Key> parser(final @Nonnull KeyParser<Key, Raw, Parsed> parser) {
        this.parsers.clear();
        this.parsers.add(parser);
        return this;
    }

    @Nonnull
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public RealStoreBuilder2<Raw, Parsed, Key> parsers(final @Nonnull List<Parser> parsers) {
        this.parsers.clear();
        for (Parser parser : parsers) {
            this.parsers.add(new NoKeyParser<>(parser));
        }
        return this;
    }

    @Nonnull
    public RealStoreBuilder2<Raw, Parsed, Key> memoryPolicy(MemoryPolicy memoryPolicy) {
        this.memoryPolicy = memoryPolicy;
        return this;
    }

    //Store will backfill the disk cache anytime a record is stale
    //User will still get the stale record returned to them
    public RealStoreBuilder2<Raw, Parsed, Key> refreshOnStale() {
        stalePolicy = StalePolicy.REFRESH_ON_STALE;
        return this;
    }

    //Store will try to get network source when disk data is stale
    //if network source throws error or is empty, stale disk data will be returned
    @Nonnull
    public RealStoreBuilder2<Raw, Parsed, Key> networkBeforeStale() {
        stalePolicy = StalePolicy.NETWORK_BEFORE_STALE;
        return this;
    }

    @Nonnull
    public Store<Parsed, Key> open() {
        if (persister == null) {
            persister = NoopPersister.create(memoryPolicy);
        }

        if (parsers.isEmpty()) {
            parser(new NoopParserFunc<Raw, Parsed>());
        }

        KeyParser<Key, Raw, Parsed> multiParser = new MultiParser<>(parsers);

        RealInternalStore2<Raw, Parsed, Key> realInternalStore
            = new RealInternalStore2<>(fetcher, persister, multiParser, memoryPolicy, stalePolicy);

        return new RealStore<>(realInternalStore);
    }
}
