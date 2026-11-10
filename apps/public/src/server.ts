import {createServer} from 'node:http';
import {AngularNodeAppEngine, createNodeRequestHandler, isMainModule, writeResponseToNodeResponse} from '@angular/ssr/node';
const engine=new AngularNodeAppEngine();
export const reqHandler=createNodeRequestHandler(async(req,res)=>{
  try{
    const response=await engine.handle(req,{contentOrigin:process.env['CONTENT_API_ORIGIN']||'http://backend:8080'});
    res.setHeader('Cache-Control','no-store');
    if(response)await writeResponseToNodeResponse(response,res);
    else {res.statusCode=404;res.end('Not found');}
  }catch{res.statusCode=503;res.end('Contenido temporalmente no disponible.');}
});
if(isMainModule(import.meta.url))createServer(reqHandler).listen(Number(process.env['PORT']||4000),'0.0.0.0');
