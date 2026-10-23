# Privacy Policy — Agentchi

*Last updated: October 21, 2026*

*[Version française](PRIVACY.fr.md)*

Agentchi is an unofficial app, not affiliated with Anthropic or OpenAI. It is a watch app (Wear OS), with no phone app.

## Data processed

**Watch app.** It displays the events from your Claude Code and Codex sessions and keeps a history of the last 30 on the watch, along with the mascot's state and your settings.

**Agentchi server.** The watch connects to the server whose address you give it, usually your own (the project's `server/` folder). The Claude Code and Codex hooks on your computers and in your cloud sessions send their events to it: folder name, short session ID, the name given to the computer, and the first 300 characters of the agent's last message. When you write to the agent, the hook reports that a message was sent (for the "Active sessions" screen): the server does not forward your text to the watch. The server does not store these events: it immediately forwards them to your watch through **Firebase Cloud Messaging** (a Google service), which keeps them for at most 24 hours if the watch is offline. To do so, the watch gives the server its FCM token. The server only keeps a hash of the pairing codes and of the watch's access token. *Settings › Server › Disconnect the watch* removes the watch, its codes and its FCM token from the server.

**Replies from the watch.** If you turn on *Reply from the watch*, the agents' questions also go through the server and Firebase: the tool and command involved, the questions and options offered, the plan or the end of the agent's last message (1,500 characters at most). Your answer (choice, typed or dictated text) goes back from the watch to the server, which passes it on to the agent (Claude Code or Codex). A pending question only lives in the server's memory, for five minutes at most, and Firebase does not keep it beyond that.

## What we don't do

- No data is sent to the app's developer.
- No analytics, advertising or trackers.
- No data is sold or shared for commercial purposes.

## Retention and deletion

Data stays on your devices and on the server you chose. *History → Clear all* empties the watch's history. Uninstalling the app deletes the watch's data.

## Contact

For any question: open an issue at https://github.com/Pelagoss/agentchi/issues
