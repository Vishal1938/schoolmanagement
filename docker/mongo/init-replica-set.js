// Initiates the single-node replica set rs0, which MongoDB requires before it will accept
// multi-document transactions (used by student creation, payments and receipts).
//
// Idempotent: re-running it on an initiated set prints the current state and exits 0.
const member = { _id: 0, host: 'localhost:27017' };

try {
  const status = rs.status();
  print(`replica set "${status.set}" already initiated, state: ${status.myState}`);
}
catch (error) {
  // NotYetInitialized (94) is the expected error on a fresh volume.
  if (error.code !== 94 && !String(error.message).includes('no replset config')) {
    throw error;
  }
  const result = rs.initiate({ _id: 'rs0', members: [member] });
  if (!result.ok) {
    throw new Error(`rs.initiate failed: ${tojson(result)}`);
  }
  print('replica set rs0 initiated');
}

// Wait until a primary has been elected, so the app does not start against a set with no primary.
for (let attempt = 0; attempt < 30; attempt++) {
  if (db.hello().isWritablePrimary) {
    print('primary elected, mongo is ready for transactions');
    quit(0);
  }
  sleep(1000);
}

print('WARNING: no primary elected within 30s');
quit(1);
