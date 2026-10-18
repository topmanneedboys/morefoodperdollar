"""Operator verification of real regional search against an active release.

Uses IAM on the private backend and checksum-verified actual source store
coordinates. This is infrastructure evidence, not an Android App Check or phone
test. Tokens remain in memory and are never written to the report.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import re
import shutil
import statistics
import subprocess
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

from google.oauth2.credentials import Credentials
from backend.object_store import GCSObjectStore


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bucket', required=True)
    parser.add_argument('--project', required=True)
    parser.add_argument('--backend-url', required=True)
    parser.add_argument('--release-id', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'https://[a-z0-9-]+\.[a-z0-9-]+\.run\.app', args.backend_url):
        raise ValueError('an exact private Cloud Run HTTPS URL is required')
    cli = shutil.which('gcloud') or 'gcloud'
    def token(kind):
        return subprocess.run([cli, 'auth', kind], capture_output=True, text=True, check=True).stdout.strip()
    store = GCSObjectStore(bucket=args.bucket, project=args.project, credentials=Credentials(token=token('print-access-token')))
    identity_token = token('print-identity-token')
    raw = store.get(f'releases/{args.release_id}/manifest.json')
    checksum = store.get(f'releases/{args.release_id}/manifest.sha256').decode('ascii').split()[0]
    if len(raw) > 2*1024*1024 or hashlib.sha256(raw).hexdigest() != checksum:
        raise ValueError('release manifest checksum mismatch')
    manifest = json.loads(raw)
    if manifest.get('releaseId') != args.release_id or manifest.get('completionState') != 'COMPLETE':
        raise ValueError('release is not complete')

    def request(path, payload=None):
        headers = {'Authorization':'Bearer '+identity_token, 'Accept':'application/json'}
        body = None
        if payload is not None:
            headers['Content-Type'] = 'application/json'
            body = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(args.backend_url+path, data=body, headers=headers)
        with urllib.request.urlopen(req, timeout=45) as response:
            raw_response = response.read(512*1024+1)
            if len(raw_response)>512*1024:
                raise ValueError('search response exceeds bound')
            return json.loads(raw_response)

    status = request('/v1/status')
    if (status.get('releaseId') != args.release_id or status.get('freshness') != 'FRESH'
            or status.get('androidNetworkingAuthorized') is not True):
        raise ValueError('backend has not activated and authorized the fresh expected release')
    descriptors = []
    for descriptor in manifest['objects']:
        match = re.fullmatch(r'micro-1024/regions/(ar-[a-z]+)/store-index\.jsonl\.gz', descriptor['path'])
        if match:
            descriptors.append((match[1], descriptor))
    regions = set(manifest['searchPackArtifact']['regions'])
    if {name for name, _ in descriptors} != regions or len(descriptors) != 24:
        raise ValueError('the expected 24-region store inventory is incomplete')

    def check(item):
        region, descriptor = item
        try:
            if descriptor['bytes'] > 1024*1024:
                raise ValueError('store index exceeds bound')
            compressed = store.get('objects/sha256/'+descriptor['sha256'])
            if len(compressed) != descriptor['bytes'] or hashlib.sha256(compressed).hexdigest() != descriptor['sha256']:
                raise ValueError('store index checksum mismatch')
            with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as handle:
                decoded = handle.read(8*1024*1024+1)
            if len(decoded)>8*1024*1024:
                raise ValueError('decompressed store index exceeds bound')
            stores = [json.loads(line) for line in decoded.splitlines() if line.strip()]
            valid = [row for row in stores if row.get('geoStatus') == 'VALID']
            lat = statistics.median(float(row['latitude']) for row in valid)
            lon = statistics.median(float(row['longitude']) for row in valid)
            point = min(valid, key=lambda row:(float(row['latitude'])-lat)**2+(float(row['longitude'])-lon)**2)
            result = request('/v1/search', {'latitude':str(point['latitude']), 'longitude':str(point['longitude']),
                                          'radiusKm':'25', 'query':'arroz', 'page':0})
            actual_regions = result.get('regionsQueried') or result.get('diagnostics',{}).get('regionsQueried',[])
            offers = [offer for match in result.get('matches',[]) for offer in match.get('qualifiedOffers',[])]
            prices = [offer for offer in offers if offer.get('listPrice',{}).get('amount') is not None]
            passed = result.get('releaseId') == args.release_id and result.get('freshness') == 'FRESH' and region in actual_regions
            case = {'region':region, 'status':'PASS' if passed else 'FAIL', 'regionsQueried':actual_regions,
                    'identityCount':len(result.get('matches',[])), 'qualifiedOffers':len(offers),
                    'listedPrices':len(prices), 'sourceStoreKey':point['storeKey'],
                    'priceEvidenceStatus':'PRESENT' if prices else 'NO_PRICE_EVIDENCE_ON_THIS_PAGE'}
        except Exception as error:
            case = {'region':region,'status':'FAIL','errorType':type(error).__name__,'error':str(error)[:240]}
        print(json.dumps(case), flush=True)
        return case
    with ThreadPoolExecutor(max_workers=2) as pool:
        cases = list(pool.map(check, sorted(descriptors)))
    report = {'status':'PASS' if all(case['status']=='PASS' for case in cases) else 'FAIL',
              'observedAt':datetime.now(timezone.utc).isoformat(), 'releaseId':args.release_id,
              'sourceReleaseDate':manifest['source']['releaseDate'],'manifestSha256':checksum,
              'regionCount':len(cases),'backendUrl':args.backend_url,
              'method':'Private IAM operator requests at checksum-verified real source store coordinates',
              'query':'arroz','cases':cases,
              'limitations':['No Motorola or customer App Check interaction was verified.',
                             'This verifies national routing/fresh responses, not every product meaning or every store.',
                             'The v1 bounded offer preview does not establish full-result price sorting.']}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f"{report['status']}: {len(cases)} regions checked",flush=True)
    return 0 if report['status']=='PASS' else 2


if __name__=='__main__':
    raise SystemExit(main())
