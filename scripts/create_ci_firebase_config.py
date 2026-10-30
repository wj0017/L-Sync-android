"""Create a non-production Firebase config for compilation and migration tests.

The identifiers are deliberately fake. Authentication and cloud services require
the developer's own Firebase project. Existing configuration is never replaced.
"""
import json
from pathlib import Path

destination = Path(__file__).resolve().parents[1] / "app" / "google-services.json"
if destination.exists():
    raise SystemExit("Refusing to overwrite app/google-services.json")

configuration = {
    "project_info": {
        "project_number": "1234567890",
        "project_id": "demo-lsync-ci",
        "storage_bucket": "demo-lsync-ci.appspot.com",
    },
    "client": [{
        "client_info": {
            "mobilesdk_app_id": "1:1234567890:android:0123456789abcdef012345",
            "android_client_info": {"package_name": "com.lsync.app"},
        },
        # The plugin generates R.string.default_web_client_id from client_type 3.
        "oauth_client": [{
            "client_id": "1234567890-ci.apps.googleusercontent.com",
            "client_type": 3,
        }],
        # Syntactically valid, deliberately non-functional placeholder.
        "api_key": [{"current_key": "AIza" + "0" * 35}],
    }],
    "configuration_version": "1",
}
destination.write_text(json.dumps(configuration, indent=2) + "\n", encoding="utf-8")
print("Created build-only Firebase configuration (no real credentials)")
