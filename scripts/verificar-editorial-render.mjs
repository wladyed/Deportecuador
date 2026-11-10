// HTTP integration test of the built Angular renderer. No browser or real database.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {once} from 'node:events';
import assert from 'node:assert/strict';

const root=new URL('../',import.meta.url);
async function exported(file){const source=await readFile(new URL('apps/public/src/'+file,root),'utf8');return JSON.parse(source.slice(source.indexOf('['),source.lastIndexOf(']')+1));}
const catalog=await exported('catalog.ts');const text=await exported('i18n.ts');
const snapshots={};
for(const [i,locale] of ['es','en','pt'].entries()){
  const pages={};
  for(const page of ['home','about','products','services','privacy'])pages[page]={metaTitle:text[i][9],metaDescription:text[i][7],...Object.fromEntries(text[i].map((value,index)=>['t'+index,value]))};
  for(const item of catalog)pages[item.id]={name:item.name[i],tag:item.tag[i],description:item.desc[i],features:item.features[i].join('\n'),metaTitle:item.name[i],metaDescription:item.desc[i]};
  snapshots[locale]=pages;
}
let fail=false;
const api=createServer((req,res)=>{res.setHeader('Content-Type','application/json');res.statusCode=fail?503:200;res.end(JSON.stringify(fail?{}:snapshots[req.url.split('/').pop()]||{}));});
api.listen(0,'127.0.0.1');await once(api,'listening');
process.env.CONTENT_API_ORIGIN='http://127.0.0.1:'+api.address().port;
let renderer;
try{
  const {reqHandler}=await import(new URL('apps/public/dist/public/server/server.mjs',root));
  renderer=createServer(reqHandler);renderer.listen(0,'127.0.0.1');await once(renderer,'listening');
  const origin='http://127.0.0.1:'+renderer.address().port;
  async function page(path,status=200){const response=await fetch(origin+path);const html=await response.text();assert.equal(response.status,status,path+' returned '+response.status);return html;}
  let count=0;
  await page('/');count++;
  for(const locale of ['es','en','pt']){
    for(const path of ['', '/products','/services','/about','/demo','/advice','/privacy']){const html=await page('/'+locale+path);assert.ok(html.includes('<h1'),path);count++;}
    for(const item of catalog){const html=await page('/'+locale+'/'+(item.kind==='product'?'products':'services')+'/'+item.id);assert.ok(html.includes(item.name[['es','en','pt'].indexOf(locale)]));count++;}
  }
  snapshots.es.asistodep.name='Cambio editorial <script>alert(1)</script>';
  snapshots.es.asistodep.metaTitle='Publicación de prueba';
  let html=await page('/es/products/asistodep');
  assert.ok(html.includes('<title>Publicación de prueba</title>'));
  assert.ok(html.includes('Cambio editorial &lt;script&gt;'));
  assert.ok(!html.includes('<script>alert(1)</script>'));
  assert.ok((await page('/en/products/asistodep')).includes('<h1>AsistoDEP</h1>'));
  snapshots.es.home.mediaImage='11111111-1111-1111-1111-111111111111';
  snapshots.es.home.mediaAlt='Imagen editorial <prueba>';
  snapshots.es.home.mediaDocument='22222222-2222-2222-2222-222222222222';
  snapshots.es.home.mediaLabel='Guía PDF';
  html=await page('/es');
  assert.ok(html.includes('/api/v1/media/'+snapshots.es.home.mediaImage));
  assert.ok(html.includes('class="hero-background"'));
  assert.ok(!html.includes('class="stadium"'));
  assert.equal(html.split('/api/v1/media/'+snapshots.es.home.mediaImage).length-1,1);
  assert.ok((await page('/en')).includes('class="stadium"'));
  delete snapshots.es.home.mediaDocument;
  assert.ok(!(await page('/es')).includes('class="section published-media"'));
  snapshots.es.home.mediaDocument='22222222-2222-2222-2222-222222222222';
  assert.ok(html.includes('/api/v1/media/'+snapshots.es.home.mediaDocument));
  assert.ok(!html.includes('alt="Imagen editorial <prueba>"'));
  assert.ok(!(await page('/en')).includes('/api/v1/media/'+snapshots.es.home.mediaImage));
  delete snapshots.es.asistodep;
  await page('/es/products/asistodep',404);
  assert.ok(!(await page('/es')).includes('href="/es/products/asistodep"'));
  await page('/es/products/no-existe',404);
  await page('/xx/demo',404);
  fail=true;html=await page('/es',503);assert.ok(!html.includes(text[0][6]));
  console.log(`${count} rutas existentes verificadas; publicación inmediata, aislamiento de idiomas, escape HTML, ocultación/404 y fallo seguro/503 correctos.`);
}finally{
  renderer?.closeAllConnections();if(renderer)await new Promise(resolve=>renderer.close(resolve));
  api.closeAllConnections();await new Promise(resolve=>api.close(resolve));
}
