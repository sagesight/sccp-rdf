package com.goodforgoodbusiness.endpoint.dht.keys.impl;

import static com.goodforgoodbusiness.rocks.RocksUtils.createCompositeKey;
import static com.goodforgoodbusiness.shared.TripleUtil.matchingCombinations;
import static com.goodforgoodbusiness.shared.encode.RDFBinary.encodeTriple;
import static java.util.stream.Collectors.toSet;

import java.util.stream.Stream;

import org.apache.jena.graph.Triple;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

import com.goodforgoodbusiness.endpoint.crypto.key.EncodeableShareKey;
import com.goodforgoodbusiness.endpoint.dht.keys.ShareKeyStore;
import com.goodforgoodbusiness.endpoint.dht.keys.ShareKeyStoreException;
import com.goodforgoodbusiness.kpabe.key.KPABEPublicKey;
import com.goodforgoodbusiness.rocks.PrefixIterator;
import com.goodforgoodbusiness.rocks.RocksManager;
import com.goodforgoodbusiness.shared.encode.JSON;
import com.goodforgoodbusiness.shared.encode.RDFBinary;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * RocksDB-backed implementation of the {@link ShareKeyStore}.
 * @author ijmad
 */
@Singleton
public class RocksKeyStore implements ShareKeyStore {
	private final RocksManager dbm;
	private final ColumnFamilyHandle tripleCFH;
	private final ColumnFamilyHandle keyCFH;
	
	@Inject
	public RocksKeyStore(RocksManager dbm) throws RocksDBException {
		this.dbm = dbm;
		this.tripleCFH = this.dbm.getOrCreateColFH("SK_TRIPLES".getBytes());
		this.keyCFH = this.dbm.getOrCreateColFH("SK_KEYS".getBytes());
	}
	
	@Override
	public Stream<KPABEPublicKey> knownContainerCreators(Triple pattern) throws ShareKeyStoreException {
		// get: triple -- result: KPABEPublicKey
		return matchingCombinations(pattern)
			.map(RDFBinary::encodeTriple)
			.flatMap(enc -> {
				try {
					return new PrefixIterator(dbm.newIterator(tripleCFH), enc).stream();
				}
				catch (RocksDBException e) {
					return Stream.empty();
				}
			})
			.map(row -> new KPABEPublicKey(row.val))
			.collect(toSet())
			.stream()
		;
	}
	
	@Override
	public Stream<EncodeableShareKey> keysForDecrypt(KPABEPublicKey publicKey, Triple tuple) throws ShareKeyStoreException {
		// get: public (XXX + tuple?) -- result: share key
		var pub = publicKey.getEncoded();
//		var enc = encodeTriple(tuple);
		
		try {
			return new PrefixIterator(dbm.newIterator(keyCFH), pub)
				.stream()
				.map(row -> JSON.decode(new String(row.val), EncodeableShareKey.class))
			;
		}
		catch (RocksDBException e) {
			return Stream.empty();
		}
		
//		
	}
	
	@Override
	public void saveKey(Triple tuple, EncodeableShareKey shareKey) throws ShareKeyStoreException {
		try {
			var enc = encodeTriple(tuple);
			var pub = shareKey.getPublic().getEncoded();
			var shk = JSON.encodeToString(shareKey); // XXX don't JSON encode 
			
			// store: tuple + RANDOM -> public
			var key1 = createCompositeKey(enc);
			dbm.put(tripleCFH, key1, pub);
			
			// store: public + tuple + RANDOM -> share key
			var key2 = createCompositeKey(pub, enc);
			dbm.put(keyCFH, key2, shk.getBytes());
		}
		catch (RocksDBException e) {
			throw new ShareKeyStoreException(e);
		}
	}
}
