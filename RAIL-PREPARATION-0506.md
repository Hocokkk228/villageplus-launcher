# VillagePlus 0.50.6

Railway preparation starts when the first station enters the construction queue. Six vanilla village candidates are checked from structure placement regions without synchronous locateStructure. One loading-only chunk ticket is active at a time; tickets are explicitly released after use. The queue yields between requests and pauses new requests when average server tick duration reaches 35 ms. Full terrain preparation proceeds in rings over 121 chunks per destination before its station is planned. Progress of discovered villages is persisted; incomplete preparation is safely rechecked after restart.

Normal-world integration test seed -2146509050469496627: six real villages discovered and 121 chunks prepared; worst planner call 0.772845 ms. Earlier getChunkFutureSyncOnMainThread implementation measured 24963.895271 ms and was replaced before delivery. Test runs at requested 200 ticks/s with no gameplay clients; this is not a shader FPS or multiplayer latency benchmark. Vanilla chunk completion still has server-thread work.

Coastal shipyards and ships remain optional third-era content; inland settlements need a suitable loaded shore. The epoch completion test does not by itself verify ports or complete rail operation.
