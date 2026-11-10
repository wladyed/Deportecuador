import {inject, Injectable, PLATFORM_ID, REQUEST_CONTEXT, RESPONSE_INIT, TransferState, makeStateKey} from '@angular/core';
import {isPlatformServer} from '@angular/common';
import {ResolveFn} from '@angular/router';
export interface ContentSnapshot {documents:Record<string,Record<string,string>>;failed:boolean}
@Injectable({providedIn:'root'})
export class PublishedContent {
  private readonly server=isPlatformServer(inject(PLATFORM_ID));
  private readonly transfer=inject(TransferState);
  private readonly context=inject(REQUEST_CONTEXT,{optional:true}) as {contentOrigin?:string}|null;
  private readonly response=inject(RESPONSE_INIT,{optional:true});
  async load(locale:string):Promise<ContentSnapshot>{
    if(!['es','en','pt'].includes(locale)){if(this.response)this.response.status=404;return {documents:{},failed:false};}
    const key=makeStateKey<ContentSnapshot>('published-'+locale);
    if(!this.server && this.transfer.hasKey(key)){const value=this.transfer.get(key,{documents:{},failed:true});this.transfer.remove(key);return value;}
    let value:ContentSnapshot;
    try{
      const origin=this.server?(this.context?.contentOrigin||'http://localhost:8081'):'';
      const result=await fetch(origin+'/api/v1/content/'+locale,{cache:'no-store',signal:AbortSignal.timeout(5000)});
      if(!result.ok)throw new Error('Content unavailable');
      value={documents:await result.json(),failed:false};
    }catch{value={documents:{},failed:true};if(this.response)this.response.status=503;}
    if(this.server)this.transfer.set(key,value);
    return value;
  }
}
export const resolveContent:ResolveFn<ContentSnapshot>=route=>inject(PublishedContent).load(route.paramMap.get('locale')||'es');
