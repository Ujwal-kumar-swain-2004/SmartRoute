import {afterEach,beforeEach,describe,it,expect,vi} from 'vitest';
import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import App from './App';
const mocks=vi.hoisted(()=>({get:vi.fn(),post:vi.fn()}));
vi.mock('axios',()=>({default:{create:()=>mocks,isAxiosError:()=>false}}));
vi.mock('./RouteMap',()=>({default:()=> <div data-testid="map">Interactive map</div>}));
const places=[{id:'p0',name:'Patia Square',category:'LANDMARK',lat:20.35,lng:85.82},{id:'p9',name:'Airport',category:'LANDMARK',lat:20.25,lng:85.81},{id:'f0',name:'Fuel stop',category:'PETROL',lat:20.33,lng:85.82}];
const route={id:'test',label:'Best route',nodes:['p0','p9'],roadIds:[],distanceKm:10,travelMinutes:25,stopMinutes:0,arrival:'2026-11-15T09:25:00',metrics:{executionMs:.2,visitedNodes:5,settledNodes:4,preprocessingMs:0},stopId:null,detourKm:0,congestion:{}};
function start(){return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><App/></QueryClientProvider>)}
beforeEach(()=>{mocks.get.mockResolvedValue({data:{places,roads:[]}});mocks.post.mockImplementation((url:string)=>Promise.resolve({data:url==='/evaluate'?[{algorithm:'DIJKSTRA',route}]:{routes:[route],stopAlternatives:[],message:'Synthetic demonstration',mode:'DEMO',algorithm:'DIJKSTRA'}}));});
afterEach(()=>{cleanup();vi.clearAllMocks()});
describe('planner interactions',()=>{
 it('renders backend results and swaps endpoints',async()=>{start();await screen.findByText('25 min');fireEvent.click(screen.getByTitle('Swap source and destination'));await waitFor(()=>expect(mocks.post).toHaveBeenLastCalledWith('/routes',expect.objectContaining({source:'p9',destination:'p0'})));});
 it('sends fuel constraints and a category stop to the backend',async()=>{start();await screen.findByText('25 min');fireEvent.click(screen.getByRole('button',{name:'Fuel'}));fireEvent.click(screen.getByLabelText('Check remaining fuel range'));await waitFor(()=>expect(mocks.post).toHaveBeenLastCalledWith('/routes',expect.objectContaining({category:'PETROL',fuelRange:8,reserve:2})));});
 it('loads measured algorithm comparison and methodology',async()=>{start();fireEvent.click(screen.getByRole('button',{name:'Algorithm lab'}));await screen.findByText('0.200');expect(mocks.post).toHaveBeenCalledWith('/evaluate',expect.anything());fireEvent.click(screen.getByRole('button',{name:'Methodology'}));expect(screen.getByText('Real algorithms. A transparent demo.')).toBeInTheDocument();});
 it('enables emergency fastest routing',async()=>{start();await screen.findByText('25 min');fireEvent.click(screen.getByLabelText('Emergency demo'));await waitFor(()=>expect(mocks.post).toHaveBeenLastCalledWith('/routes',expect.objectContaining({emergency:true,objective:'FASTEST'})));expect(screen.getByText(/Academic demonstration only/)).toBeInTheDocument();});
 it('shows a no-feasible-stop message without inventing a route',async()=>{mocks.post.mockResolvedValue({data:{routes:[],stopAlternatives:[],message:'No feasible stop is reachable within your fuel range.',mode:'DEMO',algorithm:'DIJKSTRA'}});start();expect(await screen.findByText(/No feasible stop is reachable/)).toBeInTheDocument();expect(screen.queryByText('25 min')).not.toBeInTheDocument();});
});

