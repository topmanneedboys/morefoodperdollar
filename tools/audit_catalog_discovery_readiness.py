"""Account for every provider group and identify shared search coverage gaps.

This audits existing evidence. It never assigns new product meanings from word
frequency, modifies the frozen SQLite artifact, or claims semantic accuracy.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import sqlite3
from collections import Counter, defaultdict
from pathlib import Path


def audit(index_path: Path, inventory_path: Path) -> dict:
    connection = sqlite3.connect(f"file:{index_path.resolve()}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        postings = defaultdict(lambda: defaultdict(set))
        for row in connection.execute("SELECT p.identity_key,p.predicate_id,c.kind FROM posting p JOIN concept c USING(concept_id) WHERE p.relation IN ('IS_A','FORM')"):
            postings[row['identity_key']][row['kind']].add(row['predicate_id'])
        aliases = defaultdict(set)
        for row in connection.execute("SELECT predicate_id,language FROM alias"):
            aliases[row['predicate_id']].add(row['language'])
        groups = defaultdict(list)
        for row in connection.execute("SELECT i.*, c.type_status,c.bilingual_status FROM identity i JOIN coverage c USING(identity_key) ORDER BY provider_key,identity_key"):
            groups[row['provider_key']].append(dict(row))
        counts = Counter()
        unresolved_heads = defaultdict(lambda: {'providers': set(), 'examples': []})
        with gzip.open(inventory_path, 'wt', encoding='utf-8', newline='\n') as output:
            for provider, identities in groups.items():
                resolved = sum(row['type_status'] == 'RESOLVED' for row in identities)
                state = 'ALL_IDENTITIES_TYPED' if resolved == len(identities) else 'PARTIALLY_TYPED' if resolved else 'NO_IDENTITY_TYPED'
                counts[state] += 1
                types = set().union(*(postings[row['identity_key']]['TYPE'] for row in identities))
                forms = set().union(*(postings[row['identity_key']]['FORM'] for row in identities))
                departments = set().union(*(postings[row['identity_key']]['DEPARTMENT'] for row in identities))
                if any(len(postings[row['identity_key']]['TYPE']) > 1 for row in identities):
                    counts['PROVIDERS_WITH_MULTI_TYPE_IDENTITIES'] += 1
                if types and all({'es', 'en'} <= aliases[predicate] for predicate in types):
                    counts['PROVIDERS_WITH_BILINGUAL_TYPE_PREDICATES'] += 1
                if forms:
                    counts['PROVIDERS_WITH_FORM_EVIDENCE'] += 1
                for row in identities:
                    if row['type_status'] == 'UNRESOLVED':
                        head = ' '.join(row['normalized_name'].split()[:2])
                        bucket = unresolved_heads[head]
                        bucket['providers'].add(provider)
                        if len(bucket['examples']) < 3 and row['display_name'] not in bucket['examples']:
                            bucket['examples'].append(row['display_name'])
                output.write(json.dumps({
                    'providerProductId': provider, 'readiness': state,
                    'identityCount': len(identities), 'typedIdentityCount': resolved,
                    'types': sorted(types), 'forms': sorted(forms), 'departments': sorted(departments),
                    'identities': [{
                        'identityKey': row['identity_key'], 'name': row['display_name'],
                        'types': sorted(postings[row['identity_key']]['TYPE']),
                        'forms': sorted(postings[row['identity_key']]['FORM']),
                        'bilingualStatus': row['bilingual_status'],
                    } for row in identities],
                }, ensure_ascii=False) + '\n')
        head_groups = sorted(unresolved_heads.items(), key=lambda pair: (-len(pair[1]['providers']), pair[0]))[:100]
        with index_path.open('rb') as handle:
            digest = hashlib.file_digest(handle, 'sha256').hexdigest()
        return {
            'auditKind': 'COMPLETE_PROVIDER_GROUP_INVENTORY_NOT_SEMANTIC_ACCEPTANCE',
            'indexSha256': digest,
            'providerProductsScanned': len(groups),
            'identityRecordsScanned': sum(map(len, groups.values())),
            'providerReadiness': dict(counts),
            'inventoryPath': str(inventory_path.resolve()),
            'topUnresolvedObservedPrefixes': [
                {'observedPrefix': head, 'distinctProviders': len(value['providers']), 'sourceExamples': value['examples']}
                for head, value in head_groups
            ],
            'limitations': [
                'Existing accepted labels are inventoried, not independently validated.',
                'A recurring prefix is a review candidate, not an automatically accepted type or English translation.',
                'Provider IDs and exact retailer identities remain separate; grouping does not establish package equivalence.',
                'Prices and complete offer-set sorting cannot be established from the identity-only sidecar.',
            ],
        }
    finally:
        connection.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--index', type=Path, required=True)
    parser.add_argument('--inventory', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = audit(args.index, args.inventory)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: report[key] for key in ('providerProductsScanned', 'identityRecordsScanned', 'providerReadiness')}, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
