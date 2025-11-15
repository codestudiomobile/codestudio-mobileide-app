import zipfile
import os
import stat
import io
import hashlib

def calculate_checksum(file_path):
    """Calculates SHA256 checksum of a file."""
    hasher = hashlib.sha256()
    try:
        with open(file_path, 'rb') as f:
            while True:
                chunk = f.read(4096)
                if not chunk:
                    break
                hasher.update(chunk)
        return hasher.hexdigest()
    except Exception as e:
        return f"Error: {e}"

def check_file_integrity(zip_handle, zip_info):
    """Checks for known executable files and their properties inside the zip."""

    # 1. Check for expected core files and their paths
    expected_core_files = [
        'usr/bin/sh',
        'usr/bin/bash',
        'usr/bin/login',
        'usr/bin/pkg',
        'usr/bin/dpkg',
        'usr/bin/ld', # Linker
        'usr/bin/readlink',
        'usr/etc/bash.bashrc',
        'usr/etc/profile',
    ]

    missing_files = [f for f in expected_core_files if f not in zip_handle.namelist()]
    if missing_files:
        print(f"  ❌ Missing critical core files: {', '.join(missing_files[:3])}...")
        return False

    print("  ✅ Core file structure (usr/bin/sh, etc.) is present.")

    # 2. Check executable permissions (best effort, as zip stores limited metadata)
    # Note: Python's standard zipfile module doesn't reliably preserve Unix permissions (S_IXUSR).
    # We can check for files we EXPECT to be executable and issue a strong warning.

    executables_to_check = ['usr/bin/sh', 'usr/bin/login']
    for file_name in executables_to_check:
        try:
            info = zip_handle.getinfo(file_name)
            # The external_attr holds the Unix permissions
            unix_mode = info.external_attr >> 16

            # Check for executable bit (S_IXUSR) or S_IFREG (regular file) and S_IXGRP/S_IXOTH
            if (unix_mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)):
                print(f"  ✅ Executable bit found for {file_name} (Mode: {oct(unix_mode)}).")
            else:
                print(f"  ⚠️ WARNING: Executable bit NOT set for {file_name} in zip metadata (Mode: {oct(unix_mode)}).")
                print("     * This means your *extraction* code MUST explicitly set permissions (chmod +x) after unpacking.")
        except KeyError:
            pass # Already caught by missing files check

    # 3. Check for the shebang in a known script (e.g., /usr/bin/pkg)
    try:
        with zip_handle.open('usr/bin/pkg') as pkg_file:
            first_line = pkg_file.readline().decode('utf-8').strip()
            if first_line.startswith('#!'):
                print(f"  ✅ Shebang found in usr/bin/pkg: {first_line}")
            else:
                print(f"  ❌ Shebang MISSING in usr/bin/pkg. First line: {first_line}")
    except KeyError:
        pass # File missing, caught earlier
    except Exception as e:
        print(f"  ❌ Error reading usr/bin/pkg: {e}")

    # 4. Check for shared libraries (critical for runtime)
    lib_dir_found = any(name.startswith('usr/lib/') and len(name) > len('usr/lib/') for name in zip_handle.namelist())
    if lib_dir_found:
        print("  ✅ Shared libraries (usr/lib/) appear to be present.")
    else:
        print("  ❌ Shared libraries (usr/lib/) appear to be missing or empty.")
        return False

    return True

def run_tests():
    """Main function to run tests on all architecture files."""
    architectures = ['aarch64', 'arm', 'i686', 'x86_64']
    all_files_present = True

    print("--- 🚀 Starting Termux Bootstrap File Validation ---")

    for arch in architectures:
        file_name = f"bootstrap-{arch}.zip"
        print(f"\n## 🔬 Testing {file_name}")

        if not os.path.exists(file_name):
            print(f"❌ File not found: {file_name}. Skipping tests for this architecture.")
            all_files_present = False
            continue

        file_checksum = calculate_checksum(file_name)
        print(f"  ℹ️ File Checksum (SHA256): {file_checksum}")

        try:
            with zipfile.ZipFile(file_name, 'r') as zip_ref:
                # Basic check for file count
                file_count = len(zip_ref.namelist())
                print(f"  ℹ️ Total files in archive: {file_count}")
                if file_count < 100:
                    print("  ⚠️ WARNING: File count is low (<100). This may indicate an incomplete bootstrap.")

                # Run the deep integrity checks
                check_file_integrity(zip_ref, arch)

        except zipfile.BadZipFile:
            print(f"  ❌ FAILED: {file_name} is a bad or corrupted ZIP file.")
        except Exception as e:
            print(f"  ❌ An unexpected error occurred: {e}")

    print("\n--- ✅ Bootstrap Validation Complete ---")
    if not all_files_present:
        print("⚠️ NOTE: One or more bootstrap files were missing. Please ensure all 4 are in the current directory.")

if __name__ == "__main__":
    run_tests()
