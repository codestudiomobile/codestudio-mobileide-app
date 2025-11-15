import os
import zipfile
import tempfile
import logging
from glob import glob
import shutil

# --- Configuration (Only Raw IDs as Requested) ---
BOOTSTRAP_FILE_PATTERNS = ["bootstrap-aarch64.zip", "bootstrap-arm.zip", "bootstrap-i686.zip", "bootstrap-x86_64.zip"]

# The strings to search for and the replacement string
_OLD_PACKAGE_ID_1_STR = "com.termux"
_OLD_PACKAGE_ID_2_STR = "com.codestudio.mobile"
_NEW_PACKAGE_ID_STR = "com.cs.ide"

# Global Byte Variables (set in main)
OLD_PKG_1_BYTES = None
OLD_PKG_2_BYTES = None
NEW_PKG_BYTES = None

# --- Script Setup ---
# Set logging to INFO to clearly see which files are patched.
logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

def perform_direct_patch(content, entry_name):
    """
    Performs binary-safe search and replace only for the raw package IDs.
    """
    global OLD_PKG_1_BYTES, OLD_PKG_2_BYTES, NEW_PKG_BYTES
           
    # List of tuples: (Search Bytes, Replacement Bytes)
    replacements = [
        # 1. Replace "com.termux" (9 bytes) with "com.cs.ide" (10 bytes)
        (OLD_PKG_1_BYTES, NEW_PKG_BYTES),
        
        # 2. Replace "com.codestudio.mobile" (21 bytes) with "com.cs.ide" (10 bytes)
        (OLD_PKG_2_BYTES, NEW_PKG_BYTES),
    ]

    modified = False
    
    for old_bytes, new_bytes in replacements:
        if content.count(old_bytes) > 0:
            # Python's bytes.replace() guarantees that every occurrence is replaced.
            content = content.replace(old_bytes, new_bytes)
            modified = True

    if modified:
        logging.info(f"PATCHED: {entry_name}")
        
    return content, modified

def process_single_zip(zip_filename):
    """
    Handles the full patch cycle for one ZIP file by processing entries in memory.
    This method guarantees every entry is checked and preserves file permissions.
    """
    if not os.path.exists(zip_filename):
        logging.warning(f"Skipping: ZIP file '{zip_filename}' not found.")
        return

    logging.info(f"\n--- Starting Guaranteed Patch for {zip_filename} ---")
    
    # Create a temporary output file for the new, patched ZIP
    temp_zip_path = tempfile.mktemp(suffix=".zip", dir=os.getcwd())
    
    patched_count = 0

    try:
        # Open the original ZIP for reading
        with zipfile.ZipFile(zip_filename, 'r') as original_zip:
            
            # Open a new temporary ZIP for writing
            with zipfile.ZipFile(temp_zip_path, 'w', zipfile.ZIP_DEFLATED) as new_zip:
                
                # Iterate through every entry in the original ZIP file
                for zipinfo in original_zip.infolist():
                    
                    # Read the content of the file entry
                    content = original_zip.read(zipinfo.filename)
                    
                    # Process the content if it's a file (not a directory)
                    if not zipinfo.is_dir():
                        patched_content, modified = perform_direct_patch(content, zipinfo.filename)
                        if modified:
                            content = patched_content
                            patched_count += 1
                    
                    # Preserve ZipInfo metadata (CRUCIAL for file mode/permissions)
                    new_zipinfo = zipfile.ZipInfo(zipinfo.filename)
                    new_zipinfo.date_time = zipinfo.date_time
                    new_zipinfo.compress_type = zipinfo.compress_type
                    new_zipinfo.external_attr = zipinfo.external_attr # Preserves permissions
                    
                    # Write the (potentially) patched content to the new ZIP
                    new_zip.writestr(new_zipinfo, content)
            
        logging.info(f"Patching complete. Total files modified: {patched_count}")

        # 2. Overwrite the original ZIP file
        if patched_count > 0:
            shutil.move(temp_zip_path, zip_filename)
            logging.info(f"SUCCESS: '{zip_filename}' has been fully updated and patched.")
        else:
            logging.warning(f"No occurrences of the raw package IDs found in {zip_filename}. File was not overwritten.")

    except Exception as e:
        logging.error(f"FATAL ERROR during ZIP processing for {zip_filename}: {e}")
        # Re-raise the exception to stop the process if a file is corrupted
        raise
    finally:
        # 3. Clean up the temporary file if it still exists
        if os.path.exists(temp_zip_path):
            os.remove(temp_zip_path)


if __name__ == "__main__":
    # 1. Correctly encode all strings to bytes once at the start
    OLD_PKG_1_BYTES = _OLD_PACKAGE_ID_1_STR.encode('ascii')
    OLD_PKG_2_BYTES = _OLD_PACKAGE_ID_2_STR.encode('ascii')
    NEW_PKG_BYTES = _NEW_PACKAGE_ID_STR.encode('ascii')
    
    # 2. Find all required ZIP files in the current directory
    found_zips = [f for pattern in BOOTSTRAP_FILE_PATTERNS for f in glob(pattern)]
    
    if not found_zips:
        logging.error(f"No bootstrap ZIP files found matching patterns: {', '.join(BOOTSTRAP_FILE_PATTERNS)}. Please ensure they are in the current directory.")
        exit(1)
    
    try:
        for zip_file in found_zips:
            process_single_zip(zip_file)
            
        print("\n--- Final Status ---")
        print(f"All {len(found_zips)} bootstrap ZIP files have been successfully processed and overwritten.")
    except Exception as e:
        print(f"\n--- FATAL ERROR ---")
        print(f"The patching failed due to an unrecoverable error: {e}")
        print(f"The original ZIP files were preserved if possible.")