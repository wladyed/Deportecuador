import {Component, Inject, ChangeDetectorRef, inject, RESPONSE_INIT} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {provideRouter, RouterOutlet, RouterLink, Router, ActivatedRoute, Routes, NavigationEnd} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {catalog} from './catalog';
import {text} from './i18n';
import {ContentSnapshot, resolveContent} from './content';
import {PublicAssistant} from './assistant';
@Component({selector:'app-page',standalone:true,imports:[RouterLink,FormsModule,PublicAssistant],templateUrl:'./page.html'})
class Page {
 private readonly changeDetector = inject(ChangeDetectorRef);
 snapshot:ContentSnapshot={documents:{},failed:false};
 private readonly response=inject(RESPONSE_INIT,{optional:true});
 get catalog(){return catalog.filter(x=>!!this.snapshot.documents[x.id]).map(x=>{const v=this.snapshot.documents[x.id];const item=structuredClone(x);item.name[this.lang]=v['name'];item.tag[this.lang]=v['tag'];item.desc[this.lang]=v['description'];item.features[this.lang]=v['features'].split(/\r?\n/);return item;});}
 get chatTopics(){return this.catalog.map(x=>({id:x.id,kind:x.kind,name:x.name[this.lang]}));}
 get contentKey(){return this.slug?this.slug:this.mode;}
 get unavailable(){return !this.validLocale || this.snapshot.failed || (!['demo','advice'].includes(this.mode) && !this.snapshot.documents[this.contentKey]) || (!!this.slug&&!this.item);}
 validLocale=true; text=text; lang=0; mode='home'; slug=''; selected:string[]=[]; step=1; error=''; receipt=''; busy=false; menu=false; procedure=0; requestKey=crypto.randomUUID();
 model={organization:'',country:'',name:'',email:'',phone:'',description:'',consent:false,website:''};
 constructor(private route:ActivatedRoute,private router:Router,@Inject(DOCUMENT) private document:Document){this.route.data.subscribe(data=>{this.snapshot=data['content'];const params=this.route.snapshot.params;let p=[params['locale'],params['mode'],params['slug']];this.validLocale=['es','en','pt'].includes(p[0]);this.lang=Math.max(0,['es','en','pt'].indexOf(p[0]));const nextMode=p[1]||'home';if(this.mode!==nextMode){this.selected=[];this.step=1;this.error='';this.receipt='';this.requestKey=crypto.randomUUID();this.model={organization:'',country:'',name:'',email:'',phone:'',description:'',consent:false,website:''};}this.mode=nextMode;this.slug=p[2]||'';let interest=this.route.snapshot.queryParamMap.get('interest');if(interest&&this.catalog.some(x=>x.id===interest))this.selected=[interest];this.document.documentElement.lang=['es','en','pt'][this.lang];const page=this.snapshot.documents[this.contentKey];this.document.title=this.unavailable?(this.snapshot.failed?'503':'404'):(page?.['metaTitle']||(this.item?.name[this.lang]||this.t(9))+' · DEPORTECUADOR');let meta=this.document.querySelector('meta[name=description]');if(!meta){meta=this.document.createElement('meta');meta.setAttribute('name','description');this.document.head.appendChild(meta);}meta.setAttribute('content',this.unavailable?'':page?.['metaDescription']||this.t(7));if(this.response&&this.unavailable)this.response.status=this.snapshot.failed?503:404;this.procedure=0;});}
 t(i:number){return this.snapshot.documents[this.contentKey]?.['t'+i]??text[this.lang][i];} get item(){return this.catalog.find(x=>x.id===this.slug&&x.kind===(this.mode==='products'?'product':this.mode==='services'?'service':''));} get products(){return this.catalog.filter(x=>x.kind==='product');} get services(){return this.catalog.filter(x=>x.kind==='service');} get options(){return this.mode==='demo'?this.products:this.services;}
 path(p=''){return '/'+['es','en','pt'][this.lang]+'/'+p;}
 switchLang(i:number){localStorage.setItem('de-language',['es','en','pt'][i]);this.router.navigateByUrl('/'+['es','en','pt'][i]+this.router.url.slice(3));}
 toggle(id:string){this.selected=this.selected.includes(id)?this.selected.filter(x=>x!==id):[...this.selected,id];}
 names(){return this.selected.map(id=>this.catalog.find(x=>x.id===id)?.name[this.lang]).join(', ');}
 valid(){return this.model.organization.trim()&&this.model.country&&this.model.name.trim()&&/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.model.email);}
 next(){this.error='';if(this.step===1&&!this.selected.length){this.error=this.t(42);return;}if(this.step===2&&!this.valid()){this.error=this.t(43);return;}this.step++;}
 async submit(){
  if(this.busy)return;
  this.error='';
  if(!this.selected.length){this.error=this.t(42);return;}
  if(!this.valid()||(this.mode==='advice'&&!this.model.description.trim())){this.error=this.t(43);return;}
  if(!this.model.consent){this.error=this.t(44);return;}
  this.busy=true;
  try{
   const res=await fetch('/api/v1/requests',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':this.requestKey},body:JSON.stringify({...this.model,type:this.mode==='demo'?'DEMO':'ADVICE',interests:this.selected,locale:['es','en','pt'][this.lang],policyVersion:'draft-2026-10-19'})});
   if(!res.ok)throw Error();
   this.receipt=(await res.json()).id;
  }catch{
   this.error=this.t(41);
  }finally{
   this.busy=false;
   // Notify zoneless Angular after the asynchronous fetch/JSON work finishes.
   this.changeDetector.markForCheck();
  }
 }
}
@Component({selector:'app-root',standalone:true,imports:[RouterOutlet],template:'<router-outlet />'})export class App{}
export const routes:Routes=[{path:':locale/:mode/:slug',component:Page,resolve:{content:resolveContent},runGuardsAndResolvers:'always'},{path:':locale/:mode',component:Page,resolve:{content:resolveContent},runGuardsAndResolvers:'always'},{path:':locale',component:Page,resolve:{content:resolveContent},runGuardsAndResolvers:'always'},{path:'',pathMatch:'full',redirectTo:'es'},{path:'**',redirectTo:'es'}];
