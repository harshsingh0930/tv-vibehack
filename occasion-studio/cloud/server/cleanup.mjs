import { Store } from './store.mjs';
const store = new Store(process.env.DATA_DIR ?? './data');
try {
  console.log('Expired occasions purged:', store.cleanup());
} finally {
  store.close();
}
