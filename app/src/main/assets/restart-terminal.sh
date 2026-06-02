#!/data/data/com.csmide/files/usr/bin/sh

echo "Restarting terminal environment..."
# Use the app's 'am' command to broadcast a restart intent
am broadcast -a com.csmide.app.restart_terminal > /dev/null 2>&1
# Exit the current shell to allow a clean restart
exit
