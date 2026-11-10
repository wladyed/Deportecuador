import {bootstrapApplication, BootstrapContext, provideClientHydration} from '@angular/platform-browser';
import {provideRouter} from '@angular/router';
import {provideServerRendering, withRoutes, RenderMode, ServerRoute} from '@angular/ssr';
import {App,routes} from './app';
import {catalog} from './catalog';
const serverRoutes:ServerRoute[]=[{path:'**',renderMode:RenderMode.Server}];
export default (context:BootstrapContext)=>bootstrapApplication(App,{providers:[provideRouter(routes),provideClientHydration(),provideServerRendering(withRoutes(serverRoutes))]},context);
