/*
 * Copyright 2015-2016 the original author or authors
 *
 * This software is licensed under the Apache License, Version 2.0,
 * the GNU Lesser General Public License version 2 or later ("LGPL")
 * and the WTFPL.
 * You may choose either license to govern your use of this software only
 * upon the condition that you accept all of the terms of either
 * the Apache License 2.0, the LGPL 2.1+ or the WTFPL.
 */
package de.measite.minidns.hla;

import java.io.IOException;
import java.util.Set;

import de.measite.minidns.DNSCache;
import de.measite.minidns.DNSName;
import de.measite.minidns.Question;
import de.measite.minidns.Record.TYPE;
import de.measite.minidns.cache.LRUCache;
import de.measite.minidns.cache.MiniDnsCacheFactory;
import de.measite.minidns.dnssec.DNSSECClient;
import de.measite.minidns.dnssec.DNSSECMessage;
import de.measite.minidns.dnssec.UnverifiedReason;
import de.measite.minidns.iterative.ReliableDNSClient.Mode;
import de.measite.minidns.record.Data;

public class DnssecResolverApi extends ResolverApi {

    public static final DnssecResolverApi INSTANCE = new DnssecResolverApi();

    private final DNSSECClient dnssecClient;
    private final DNSSECClient iterativeOnlyDnssecClient;
    private final DNSSECClient recursiveOnlyDnssecClient;

    public DnssecResolverApi() {
        this(new MiniDnsCacheFactory() {
            @Override
            public DNSCache newCache() {
                return new LRUCache(1024);
            }
        });
    }

    public DnssecResolverApi(MiniDnsCacheFactory cacheFactory) {
        this(new DNSSECClient(cacheFactory.newCache()), cacheFactory);
    }

    private DnssecResolverApi(DNSSECClient dnssecClient, MiniDnsCacheFactory cacheFactory) {
        super(dnssecClient);
        this.dnssecClient = dnssecClient;

        // Set the *_ONLY_DNSSEC ResolverApi. It is important that the two do *not* share the same cache, since we
        // probably fall back to iterativeOnly and in that case do not want the cached results of the recursive result.
        iterativeOnlyDnssecClient = new DNSSECClient(cacheFactory.newCache());
        iterativeOnlyDnssecClient.setMode(Mode.iterativeOnly);

        recursiveOnlyDnssecClient = new DNSSECClient(cacheFactory.newCache());
        recursiveOnlyDnssecClient.setMode(Mode.recursiveOnly);
    }

    @Override
    public <D extends Data> ResolverResult<D> resolve(Question question) throws IOException {
        DNSSECMessage dnssecMessage = dnssecClient.queryDnssec(question);
        return toResolverResult(question, dnssecMessage);
    }

    /**
     * Resolve the given name and type which is expected to yield DNSSEC authenticated results.
     *
     * @param name the DNS name to resolve.
     * @param type the class of the RR type to resolve.
     * @param <D> the RR type to resolve.
     * @return the resolver result.
     * @throws IOException in case an exception happens while resolving.
     * @see #resolveDnssecReliable(Question)
     */
    public <D extends Data> ResolverResult<D> resolveDnssecReliable(String name, Class<D> type) throws IOException {
        return resolveDnssecReliable(DNSName.from(name), type);
    }

    /**
     * Resolve the given name and type which is expected to yield DNSSEC authenticated results.
     *
     * @param name the DNS name to resolve.
     * @param type the class of the RR type to resolve.
     * @param <D> the RR type to resolve.
     * @return the resolver result.
     * @throws IOException in case an exception happens while resolving.
     * @see #resolveDnssecReliable(Question)
     */
    public <D extends Data> ResolverResult<D> resolveDnssecReliable(DNSName name, Class<D> type) throws IOException {
        TYPE t = TYPE.getType(type);
        Question q = new Question(name, t);
        return resolveDnssecReliable(q);
    }

    /**
     * Resolve the given question which is expected to yield DNSSEC authenticated results.
     *
     * @param question the question to resolve.
     * @param <D> the RR type to resolve.
     * @return the resolver result.
     * @throws IOException in case an exception happens while resolving.
     */
    public <D extends Data> ResolverResult<D> resolveDnssecReliable(Question question) throws IOException {
        DNSSECMessage dnssecMessage = recursiveOnlyDnssecClient.queryDnssec(question);
        if (dnssecMessage == null || !dnssecMessage.authenticData) {
            dnssecMessage = iterativeOnlyDnssecClient.queryDnssec(question);
        }
        return toResolverResult(question, dnssecMessage);
    }

    public DNSSECClient getDnssecClient() {
        return dnssecClient;
    }

    private static <D extends Data> ResolverResult<D> toResolverResult(Question question, DNSSECMessage dnssecMessage) {
        Set<UnverifiedReason> unverifiedReasons = dnssecMessage.getUnverifiedReasons();

        return new ResolverResult<D>(question, dnssecMessage, unverifiedReasons);
    }
}
